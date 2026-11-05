package br.com.autocorrida

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import br.com.autocorrida.core.Decisao
import br.com.autocorrida.core.EstadoAtual
import br.com.autocorrida.core.LeitorDeEstado
import br.com.autocorrida.core.LeitorDeOferta
import br.com.autocorrida.core.Limites
import br.com.autocorrida.core.LocalizadorDeRecusa
import br.com.autocorrida.core.MotorDeRegras
import br.com.autocorrida.core.NoDaTela
import br.com.autocorrida.core.PadroesAtivos
import br.com.autocorrida.core.Plataforma
import br.com.autocorrida.core.RastreadorDeCorrida

/**
 * Lê a tela dos apps de motorista. A cada mudança de tela:
 *  1. Se for uma oferta: avalia com as regras + estado da corrida atual e, se aprovada, toca em Aceitar.
 *  2. Se não for oferta: atualiza o estado (livre / indo buscar / em viagem + km restante).
 */
class AceiteAccessibilityService : AccessibilityService() {

    private companion object {
        const val TAG = "CorridaAuto"
        const val MIN_NOS_TELA_SEM_TEXTO = 10
        const val MAX_TEXTOS_TELA_SEM_TEXTO = 5
        const val INTERVALO_RELEITURA_MS = 700L
        const val MAX_RELEITURAS = 25
    }

    private val handler = Handler(Looper.getMainLooper())
    /** Um rastreador por app: estar livre no Uber não apaga a corrida em andamento na 99. */
    private val rastreadores = mutableMapOf<String, RastreadorDeCorrida>()
    private fun rastreador(pacote: String) = rastreadores.getOrPut(pacote) { RastreadorDeCorrida() }

    /** Estado mais "ocupado" entre os apps: em viagem > indo buscar > livre. */
    private fun combinar(estados: List<EstadoAtual>): EstadoAtual =
        estados.maxByOrNull { it.fase.ordinal } ?: EstadoAtual.LIVRE

    private fun estadoGeral() = combinar(rastreadores.values.map { it.estado })
    private fun estadoParaDecisao() = combinar(rastreadores.values.map { it.estadoParaDecisao() })
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
        Log.i(TAG, "serviço conectado, versão ${BuildConfig.VERSION_NAME}, sdk ${Build.VERSION.SDK_INT}")
        config = Configuracoes(this)
        config.observar(aoMudarConfig)
        aplicarBolinha()
        AtualizacaoRemota.carregarCache(this)
        AtualizacaoRemota.verificar(this, forcar = true)
    }

    /** Agrupa eventos: a tela gera dezenas por segundo; processa no máximo a cada ~150 ms. */
    private var agendado = false
    private val processarAgora = Runnable { agendado = false; processarJanelas() }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::config.isInitialized) return
        // Ignora eventos do próprio app (bolinha, tela de configuração)
        if (event.packageName?.toString() == packageName) return
        if (!agendado) {
            agendado = true
            handler.postDelayed(processarAgora, 150)
        }
    }

    /**
     * Olha TODAS as janelas visíveis (o pop-up de oferta pode ser uma janela por cima de outro app)
     * e processa só as que pertencem aos apps de corrida.
     */
    private fun processarJanelas() {
        AtualizacaoRemota.verificar(this) // no máximo a cada 5 min; roda em segundo plano
        val lista = runCatching { windows }
            .onFailure { Log.e(TAG, "falha ao listar janelas", it) }
            .getOrNull().orEmpty()
        logarJanelas(lista)
        Diagnostico.registrarJanelas {
            lista.joinToString("\n") { w ->
                val r = Rect().also(w::getBoundsInScreen)
                "${w.root?.packageName ?: "?"} tipo=${w.type} camada=${w.layer} ativa=${w.isActive} @${r.toShortString()} \"${w.title ?: ""}\""
            }
        }
        val porPacote = lista.mapNotNull { it.root }
            .groupBy { it.packageName?.toString() ?: "" }
            .filterKeys { Plataforma.doPacote(it) != Plataforma.DESCONHECIDA }
        for ((pacote, raizes) in porPacote) processarApp(pacote, raizes)
    }

    private fun processarApp(pacote: String, raizes: List<AccessibilityNodeInfo>) {
        val plataforma = Plataforma.doPacote(pacote)
        val nos = raizes.flatMap { coletarNos(it) }
        val textos = nos.mapNotNull { it.texto ?: it.descricao }

        val oferta = LeitorDeOferta.ler(textos, plataforma)
        logarLeitura(pacote, raizes.size, nos.size, textos, oferta != null)
        if (oferta == null && nos.size >= MIN_NOS_TELA_SEM_TEXTO && textos.size <= MAX_TEXTOS_TELA_SEM_TEXTO) {
            logarArvore(pacote, nos)
            agendarReleitura()
        } else {
            releiturasSeguidas = 0
        }
        if (oferta == null) {
            val leitura = LeitorDeEstado.ler(textos)
            Diagnostico.registrarTela(pacote, nos, "estado lido: ${leitura?.fase ?: "nenhum"} km restante: ${leitura?.kmRestante}")
            leitura?.let {
                val estavaEmCorrida = estadoGeral().emCorrida
                rastreador(pacote).atualizar(it)
                Historico.estado = estadoGeral()
                if (estavaEmCorrida != Historico.estado.emCorrida) bolinha?.atualizar()
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
        val decisao = MotorDeRegras.avaliar(oferta, regras, estadoParaDecisao())
        Log.i(TAG, "oferta valor=${oferta.valor} busca=${oferta.kmBusca} viagem=${oferta.kmViagem} decisao=${decisao::class.simpleName} recusar=${MotorDeRegras.deveRecusar(decisao, regras)}")
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

    // ---- Logs para depuração por adb: `adb logcat -s CorridaAuto`. Só registram quando algo muda. ----
    private var ultimasJanelas: String? = null
    private val ultimaLeitura = mutableMapOf<String, String>()

    private fun logarJanelas(lista: List<android.view.accessibility.AccessibilityWindowInfo>) {
        val resumo = lista.joinToString(" | ") { w ->
            "${w.root?.packageName ?: "sem-raiz"} tipo=${w.type} ativa=${w.isActive}"
        }
        if (resumo != ultimasJanelas) {
            ultimasJanelas = resumo
            Log.i(TAG, "janelas(${lista.size}): $resumo")
        }
    }

    /**
     * Tela com muitos elementos e quase nenhum texto: pode ser cache de nós desatualizado, pois o app de
     * corrida nem sempre avisa quando o conteúdo muda. Limpa o cache e lê de novo enquanto durar.
     */
    private var releituraAgendada = false
    private var releiturasSeguidas = 0
    private val releitura = Runnable {
        releituraAgendada = false
        releiturasSeguidas++
        val limpou = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && clearCache()
        Log.i(TAG, "releitura #$releiturasSeguidas cacheLimpo=$limpou")
        processarJanelas()
    }

    private fun agendarReleitura() {
        if (releituraAgendada || releiturasSeguidas >= MAX_RELEITURAS) return
        releituraAgendada = true
        handler.postDelayed(releitura, INTERVALO_RELEITURA_MS)
    }

    private fun logarArvore(pacote: String, nos: List<No>) {
        val arvore = nos.joinToString(" ") { n ->
            val r = n.limites
            "${n.classe}${if (n.clicavel) "[C]" else ""}${n.id?.let { "#" + it.substringAfter(":id/") } ?: ""}@${r.left},${r.top},${r.right},${r.bottom}"
        }
        arvore.chunked(3000).forEach { Log.i(TAG, "arvore $pacote nos=${nos.size}: $it") }
    }

    private fun logarLeitura(pacote: String, raizes: Int, nos: Int, textos: List<String>, ehOferta: Boolean) {
        val conteudo = textos.joinToString(" ¦ ")
        if (ultimaLeitura[pacote] == conteudo) return
        ultimaLeitura[pacote] = conteudo
        Log.i(TAG, "leitura $pacote raizes=$raizes nos=$nos oferta=$ehOferta: ${conteudo.take(1500)}")
    }

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
                Log.i(TAG, Diagnostico.ultimaAcao)
                t.aoTerminar?.invoke(como != null)
            } else {
                handler.postDelayed(passo, 400)
            }
        }
        handler.postDelayed(passo, config.atrasoMs)
        return t
    }

    /** Só as janelas do app indicado (nunca a janela de outro app que esteja na frente). */
    private fun raizesDoApp(pacote: String): List<AccessibilityNodeInfo> =
        runCatching { windows }.getOrNull().orEmpty()
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == pacote }

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
        val nos = nosAtuais(pacote)
        val tela = resources.displayMetrics
        val noDaTela = nos.map { n ->
            val r = n.limites
            NoDaTela(n.texto, n.descricao, n.id, n.clicavel, Limites(r.left, r.top, r.right, r.bottom))
        }
        val alvo = LocalizadorDeRecusa.localizar(noDaTela, tela.widthPixels, tela.heightPixels) ?: return null
        return if (clicar(nos[alvo.indice])) alvo.como else null
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
