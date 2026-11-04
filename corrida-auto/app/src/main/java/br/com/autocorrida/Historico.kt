package br.com.autocorrida

import br.com.autocorrida.core.Decisao
import br.com.autocorrida.core.EstadoAtual
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Últimas decisões, em memória, para mostrar na tela do app. */
object Historico {
    private const val MAX = 50
    private val itens = ArrayDeque<String>()
    private val hora = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))

    @Volatile var estado: EstadoAtual = EstadoAtual.LIVRE
    var aoMudar: (() -> Unit)? = null

    @Synchronized
    fun registrar(d: Decisao, tocou: Boolean? = null) {
        val o = d.oferta
        val base = "%s %s R$ %.2f | busca %s | viagem %s".format(
            hora.format(Date()), o.plataforma, o.valor, km(o.kmBusca), km(o.kmViagem),
        )
        val linha = when (d) {
            is Decisao.Aceitar -> buildString {
                append("✅ ").append(base)
                append(" | R$ %.2f/km".format(d.valorPorKm ?: 0.0))
                if (d.emSequencia) append(" | sequência, busca do fim: ${km(d.kmBuscaUsado)}")
                if (tocou == false) append(" | ⚠ botão Aceitar não encontrado")
            }
            is Decisao.Recusar -> "❌ $base | ${d.motivos.joinToString("; ")}"
        }
        itens.addFirst(linha)
        while (itens.size > MAX) itens.removeLast()
        aoMudar?.invoke()
    }

    @Synchronized fun texto(): String = itens.joinToString("\n\n")

    private fun km(v: Double?) = v?.let { "%.1f km".format(it) } ?: "?"
}
