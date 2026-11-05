package br.com.autocorrida

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Um nó da tela, já com os dados que interessam. */
class No(
    val info: AccessibilityNodeInfo,
    val texto: String?,
    val descricao: String?,
    val id: String?,
    val classe: String,
    val clicavel: Boolean,
    val limites: Rect,
)

/**
 * Guarda o que o app leu nas últimas telas dos apps de corrida, para o motorista
 * copiar e mandar para ajuste ("Copiar diagnóstico" na tela do app).
 */
object Diagnostico {
    private val hora = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))

    @Volatile private var ultimaOferta: String = ""
    /** Última tela de cada app (por pacote). */
    private val telas = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Última tela com "R$" que NÃO foi reconhecida como oferta, por pacote. */
    private val telasComValor = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val registradoEm = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var janelas: String = ""
    @Volatile private var janelasEm = 0L
    @Volatile var ultimaAcao: String = ""

    fun registrarOferta(pacote: String, nos: List<No>, resumo: String) {
        ultimaOferta = formatar("OFERTA", pacote, nos, resumo)
    }

    /** Telas que não são oferta (corrida em andamento etc.); no máximo 1 por segundo por app. */
    fun registrarTela(pacote: String, nos: List<No>, resumo: String) {
        val agora = System.currentTimeMillis()
        if (agora - (registradoEm[pacote] ?: 0L) < 1000) return
        registradoEm[pacote] = agora
        val texto = formatar("TELA", pacote, nos, resumo)
        telas[pacote] = texto
        if (nos.any { (it.texto ?: it.descricao ?: "").contains("R$") }) telasComValor[pacote] = texto
    }

    /** Lista de janelas visíveis (pacote, tipo, posição), no máximo a cada 2 s. */
    fun registrarJanelas(descricao: () -> String) {
        val agora = System.currentTimeMillis()
        if (agora - janelasEm < 2000) return
        janelasEm = agora
        janelas = "== JANELAS ${hora.format(Date())} ==\n" + descricao()
    }

    fun texto(): String = buildString {
        appendLine("Corrida Auto ${BuildConfig.VERSION_NAME} – diagnóstico")
        appendLine("Padrões de leitura v${br.com.autocorrida.core.PadroesAtivos.atual.versao}")
        if (ultimaAcao.isNotEmpty()) appendLine("Última ação: $ultimaAcao")
        appendLine()
        appendLine(ultimaOferta.ifEmpty { "(nenhuma oferta lida ainda)" })
        appendLine()
        appendLine(janelas.ifEmpty { "(nenhuma janela registrada)" })
        telasComValor.forEach { (p, t) -> appendLine(); appendLine("-- tela com R$ não reconhecida ($p) --"); appendLine(t) }
        telas.forEach { (p, t) -> if (telasComValor[p] != t) { appendLine(); appendLine(t) } }
    }

    private fun formatar(tipo: String, pacote: String, nos: List<No>, resumo: String) = buildString {
        appendLine("== $tipo ${hora.format(Date())} $pacote ==")
        appendLine(resumo)
        nos.take(250).forEach { n ->
            if (n.texto == null && n.descricao == null && n.id == null && !n.clicavel) return@forEach
            append(if (n.clicavel) "[C] " else "    ")
            append(n.classe)
            n.texto?.let { append(" t=\"").append(it.replace('\n', ' ')).append('"') }
            n.descricao?.let { append(" d=\"").append(it.replace('\n', ' ')).append('"') }
            n.id?.let { append(" id=").append(it.substringAfter(":id/")) }
            val r = n.limites
            append(" @").append(r.left).append(',').append(r.top).append('-').append(r.right).append(',').append(r.bottom)
            appendLine()
        }
    }
}
