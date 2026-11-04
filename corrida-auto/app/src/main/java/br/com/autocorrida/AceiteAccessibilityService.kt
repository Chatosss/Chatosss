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
        val textos = raizes.flatMap { coletarTextos(it) }

        val oferta = LeitorDeOferta.ler(textos, plataforma)
        if (oferta == null) {
            LeitorDeEstado.ler(textos)?.let {
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

        val decisao = MotorDeRegras.avaliar(oferta, config.carregar(), rastreador.estadoParaDecisao())
        if (decisao !is Decisao.Aceitar) {
            Historico.registrar(decisao)
            return
        }

        handler.postDelayed({
            val tocou = tocarEmAceitar(pacote)
            Historico.registrar(decisao, tocou)
        }, config.atrasoMs)
    }

    override fun onInterrupt() {}

    private fun raizesDoApp(pacote: String): List<AccessibilityNodeInfo> {
        val porJanela = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pacote }
        return porJanela.ifEmpty { listOfNotNull(rootInActiveWindow) }
    }

    private fun coletarTextos(no: AccessibilityNodeInfo, saida: MutableList<String> = mutableListOf()): List<String> {
        no.text?.toString()?.takeIf { it.isNotBlank() }?.let(saida::add)
        if (no.text.isNullOrBlank()) no.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(saida::add)
        for (i in 0 until no.childCount) no.getChild(i)?.let { coletarTextos(it, saida) }
        return saida
    }

    /** Procura o botão Aceitar e clica; se o nó não for clicável, toca no centro dele. */
    private fun tocarEmAceitar(pacote: String): Boolean {
        for (raiz in raizesDoApp(pacote)) {
            val alvo = acharAceitar(raiz) ?: continue
            var no: AccessibilityNodeInfo? = alvo
            while (no != null) {
                if (no.isClickable && no.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                no = no.parent
            }
            val r = Rect().also(alvo::getBoundsInScreen)
            if (!r.isEmpty) return tocar(r.exactCenterX(), r.exactCenterY())
        }
        return false
    }

    private fun acharAceitar(no: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val t = (no.text ?: no.contentDescription)?.toString()
        if (t != null && PadroesAtivos.atual.ehRotuloAceitar(t)) return no
        for (i in 0 until no.childCount) {
            no.getChild(i)?.let { filho -> acharAceitar(filho)?.let { return it } }
        }
        return null
    }

    private fun tocar(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesto = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesto, null, null)
    }
}
