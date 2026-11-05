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
    @Volatile private var ultimaTela: String = ""
    @Volatile private var ultimaTelaEm = 0L
    @Volatile var ultimaAcao: String = ""

    fun registrarOferta(pacote: String, nos: List<No>, resumo: String) {
        ultimaOferta = formatar("OFERTA", pacote, nos, resumo)
    }

    /** Telas que não são oferta (corrida em andamento etc.); no máximo 1 por segundo. */
    fun registrarTela(pacote: String, nos: List<No>, resumo: String) {
        val agora = System.currentTimeMillis()
        if (agora - ultimaTelaEm < 1000) return
        ultimaTelaEm = agora
        ultimaTela = formatar("TELA", pacote, nos, resumo)
    }

    fun texto(): String = buildString {
        appendLine("Corrida Auto ${BuildConfig.VERSION_NAME} – diagnóstico")
        if (ultimaAcao.isNotEmpty()) appendLine("Última ação: $ultimaAcao")
        appendLine()
        appendLine(ultimaOferta.ifEmpty { "(nenhuma oferta lida ainda)" })
        appendLine()
        append(ultimaTela.ifEmpty { "(nenhuma outra tela lida ainda)" })
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
