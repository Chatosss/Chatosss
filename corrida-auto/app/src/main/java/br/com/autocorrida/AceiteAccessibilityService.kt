package br.com.autocorrida

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import br.com.autocorrida.core.Decisao
import br.com.autocorrida.core.LeitorDeEstado
import br.com.autocorrida.core.LeitorDeOferta
import br.com.autocorrida.core.MotorDeRegras
import br.com.autocorrida.core.PadroesAtivos
import br.com.autocorrida.core.Plataforma
import br.com.autocorrida.core.RastreadorDeCorrida

/**
 * Lê a tela dos apps de motorista. A cada mudança de tela:
 *  1. Se for uma oferta: avalia com as regras + estado da corrida atual e, se aprovada, toca em Aceitar.
 *  2. Se não for oferta: atualiza o estado (livre / indo buscar / em viagem + km restante).
 */
class AceiteAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val rastreador = RastreadorDeCorrida()
    private lateinit var config: Configuracoes

    /** Evita avaliar/tocar várias vezes na mesma oferta (a tela gera muitos eventos). */
    private var ultimaAssinatura: String? = null
    private var ultimaAssinaturaEm = 0L

    private var bolinha: BolinhaFlutuante? = null

    /** Reage às mudanças de configuração (feitas pela bolinha ou pela tela do app). */
    private val aoMudarConfig = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
        handler.post {
            when (chave) {
                "ativo" -> bolinha?.atualizar()
                "mostrarBolinha" -> aplicarBolinha()
            }
        }
    }

    private fun aplicarBolinha() {
        val b = bolinha ?: BolinhaFlutuante(this, config).also { bolinha = it }
        if (config.mostrarBolinha) b.mostrar() else b.esconder()
    }

    override fun onDestroy() {
        if (::config.isInitialized) config.pararDeObservar(aoMudarConfig)
        bolinha?.esconder()
        bolinha = null
        super.onDestroy()
    }

    override fun onServiceConnected() {
        config = Configuracoes(this)
        config.observar(aoMudarConfig)
        aplicarBolinha()
        AtualizacaoRemota.carregarCache(this)
        AtualizacaoRemota.verificar(this, forcar = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pacote = event.packageName?.toString() ?: return
        val plataforma = Plataforma.doPacote(pacote)
        if (plataforma == Plataforma.DESCONHECIDA) return
        AtualizacaoRemota.verificar(this) // no máximo a cada 5 min; roda em segundo plano

        val raizes = raizesDoApp(pacote)
        if (raizes.isEmpty()) return
        val nos = raizes.flatMap { coletarNos(it) }
        val textos = nos.mapNotNull { it.texto ?: it.descricao }

        val oferta = LeitorDeOferta.ler(textos, plataforma)
        if (oferta == null) {
            val leitura = LeitorDeEstado.ler(textos)
            Diagnostico.registrarTela(pacote, nos, "estado lido: ${leitura?.fase ?: "nenhum"} km restante: ${leitura?.kmRestante}")
            leitura?.let {
                val estavaEmCorrida = Historico.estado.emCorrida
                rastreador.atualizar(it)
                Historico.estado = rastreador.estado
                if (estavaEmCorrida != rastreador.estado.emCorrida) bolinha?.atualizar()
                Historico.aoMudar?.invoke()
            }
            return
        }

        val assinatura = "${oferta.plataforma}|${oferta.valor}|${oferta.kmBusca}|${oferta.kmViagem}|${oferta.destino}"
        val agora = System.currentTimeMillis()
        if (assinatura == ultimaAssinatura && agora - ultimaAssinaturaEm < 30_000) return
        ultimaAssinatura = assinatura
        ultimaAssinaturaEm = agora

        val regras = config.carregar()
        val decisao = MotorDeRegras.avaliar(oferta, regras, rastreador.estadoParaDecisao())
        Diagnostico.registrarOferta(
            pacote, nos,
            "valor=${oferta.valor} busca=${oferta.kmBusca} viagem=${oferta.kmViagem} " +
                "origem=\"${oferta.origem}\" destino=\"${oferta.destino}\" decisão=$decisao",
        )
        when {
            decisao is Decisao.Aceitar -> tentar(pacote, "aceitar") { tocarAceitar(pacote) }.also { r ->
                r.aoTerminar = { ok -> Historico.registrar(decisao, tocou = ok) }
            }

            MotorDeRegras.deveRecusar(decisao, regras) -> tentar(pacote, "recusar") { tocarRecusar(pacote) }.also { r ->
                r.aoTerminar = { ok -> Historico.registrar(decisao, recusou = ok) }
            }

            else -> Historico.registrar(decisao)
        }
    }

    override fun onInterrupt() {}

    /** Executa [acao] após o atraso configurado; se não achar o botão, tenta mais 3 vezes (a tela pode estar montando). */
    private class Tentativa { var aoTerminar: ((Boolean) -> Unit)? = null }

    private fun tentar(pacote: String, nome: String, acao: () -> String?): Tentativa {
        val t = Tentativa()
        var restantes = 4
        lateinit var passo: Runnable
        passo = Runnable {
            restantes--
            val como = acao()
            if (como != null || restantes <= 0) {
                Diagnostico.ultimaAcao = if (como != null) "$nome: tocou ($como)" else "$nome: botão não encontrado em $pacote"
                t.aoTerminar?.invoke(como != null)
            } else {
                handler.postDelayed(passo, 400)
            }
        }
        handler.postDelayed(passo, config.atrasoMs)
        return t
    }

    private fun raizesDoApp(pacote: String): List<AccessibilityNodeInfo> {
        val porJanela = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pacote }
        return porJanela.ifEmpty { listOfNotNull(rootInActiveWindow) }
    }

    private fun coletarNos(no: AccessibilityNodeInfo, saida: MutableList<No> = mutableListOf()): List<No> {
        if (saida.size > 600) return saida
        val r = Rect().also(no::getBoundsInScreen)
        saida += No(
            info = no,
            texto = no.text?.toString()?.takeIf { it.isNotBlank() },
            descricao = no.contentDescription?.toString()?.takeIf { it.isNotBlank() },
            id = no.viewIdResourceName,
            classe = no.className?.toString()?.substringAfterLast('.') ?: "?",
            clicavel = no.isClickable,
            limites = r,
        )
        for (i in 0 until no.childCount) no.getChild(i)?.let { coletarNos(it, saida) }
        return saida
    }

    private fun nosAtuais(pacote: String) = raizesDoApp(pacote).flatMap { coletarNos(it) }

    /** Toca no botão Aceitar. Retorna como achou o botão, ou null. */
    private fun tocarAceitar(pacote: String): String? {
        val alvo = nosAtuais(pacote).firstOrNull { n ->
            listOfNotNull(n.texto, n.descricao).any { PadroesAtivos.atual.ehRotuloAceitar(it) }
        } ?: return null
        return if (clicar(alvo)) "texto \"${alvo.texto ?: alvo.descricao}\"" else null
    }

    /**
     * Toca no botão de recusar. Ordem de busca:
     *  1. botão com texto "Recusar", "Rejeitar"…
     *  2. botão "X" identificado pela descrição ("Fechar", "close") ou pelo id (…close…)
     *  3. botão pequeno, sem texto, no canto de cima da oferta, na altura do valor (o "X" da 99/Uber)
     */
    private fun tocarRecusar(pacote: String): String? {
        val p = PadroesAtivos.atual
        val nos = nosAtuais(pacote)

        nos.firstOrNull { n -> listOfNotNull(n.texto, n.descricao).any { p.ehRotuloRecusar(it) } }
            ?.let { if (clicar(it)) return "texto \"${it.texto ?: it.descricao}\"" }

        nos.firstOrNull { n ->
            listOfNotNull(n.texto, n.descricao).any { p.ehRotuloFechar(it) } || (n.id != null && p.ehIdFechar(n.id))
        }?.let { if (clicar(it)) return "fechar \"${it.descricao ?: it.texto ?: it.id}\"" }

        acharXPelaPosicao(nos)?.let { if (clicar(it)) return "X pela posição @${it.limites.toShortString()}" }
        return null
    }

    /** O "X" fica no canto superior direito do cartão da oferta, na faixa acima/ao lado do valor. */
    private fun acharXPelaPosicao(nos: List<No>): No? {
        val tela = resources.displayMetrics
        val valor = nos.firstOrNull { n ->
            val t = n.texto ?: n.descricao ?: return@firstOrNull false
            t.contains("R$") && !t.contains("/km") && !t.contains("/min") && !n.limites.isEmpty
        } ?: return null
        val alturaMax = tela.heightPixels * 0.12f
        return nos.filter { n ->
            val r = n.limites
            n.clicavel && n.texto == null && !r.isEmpty &&
                r.width() < tela.widthPixels * 0.3f && r.height() < tela.heightPixels * 0.12f &&
                r.centerX() > tela.widthPixels * 0.6f &&
                r.centerY() < valor.limites.bottom && r.centerY() > valor.limites.top - alturaMax
        }.maxWithOrNull(compareBy<No> { it.limites.centerX() }.thenBy { -it.limites.centerY() })
    }

    /** Clica no nó (ou no primeiro pai clicável); se não der, toca no centro dele. */
    private fun clicar(n: No): Boolean {
        var no: AccessibilityNodeInfo? = n.info
        while (no != null) {
            if (no.isClickable && no.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            no = no.parent
        }
        val r = n.limites
        return !r.isEmpty && tocar(r.exactCenterX(), r.exactCenterY())
    }

    private fun tocar(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesto = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesto, null, null)
    }
}
