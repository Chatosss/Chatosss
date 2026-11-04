package br.com.autocorrida.core

import java.text.Normalizer

internal object Texto {
    private val acentos = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    /** Minúsculas, sem acentos e com espaços simples: "Jardim  São Paulo" -> "jardim sao paulo". */
    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(acentos, "")
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .trim()

    /** Verifica se [agulha] aparece em [palheiro] como palavra(s) inteira(s). */
    fun contemPalavra(palheiro: String, agulha: String): Boolean =
        Regex("(^|[^a-z0-9])" + Regex.escape(agulha) + "($|[^a-z0-9])").containsMatchIn(palheiro)

    /** "1.234,56" / "12,50" / "12.5" -> Double */
    fun numeroBr(s: String): Double? {
        val limpo = s.trim()
        val normal = when {
            limpo.contains(',') -> limpo.replace(".", "").replace(',', '.')
            else -> limpo
        }
        return normal.toDoubleOrNull()
    }
}
