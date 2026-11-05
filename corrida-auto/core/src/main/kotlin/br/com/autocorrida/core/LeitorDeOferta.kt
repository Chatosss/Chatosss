package br.com.autocorrida.core

/**
 * Converte os textos lidos da tela (na ordem em que aparecem) em uma [Oferta].
 *
 * Heurística usada (vale para os layouts atuais de Uber, 99 e inDrive):
 *  - O valor é o primeiro "R$ xx,xx" que não seja um valor por km/min nem um adicional ("+R$").
 *  - A primeira distância encontrada é a busca (até o passageiro) e a última é a viagem.
 *    Com uma única distância, ela é considerada a viagem.
 *  - Linhas entre a 1ª e a última distância = origem; linhas após a última distância = destino.
 *
 * Os apps mudam o layout com frequência; por isso as regras de leitura ficam todas aqui
 * e são cobertas por testes.
 */
object LeitorDeOferta {

    private val regexValor = Regex("""(\+\s*)?R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)(\s*/\s*(km|min))?""", RegexOption.IGNORE_CASE)
    private val regexKm = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val regexMetros = Regex("""(?<![\d.,])(\d{1,4})\s*m\b(?!in)""", RegexOption.IGNORE_CASE)
    private val regexTempoDistancia = Regex("""\d+\s*min(?:uto)?s?\b[^(]*\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)\s*\)""", RegexOption.IGNORE_CASE)

    fun ler(textos: List<String>, plataforma: Plataforma, padroes: Padroes = PadroesAtivos.atual): Oferta? {
        val gatilhos = padroes.gatilhosOferta
        val linhas = textos.map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.isEmpty()) return null

        val tudo = linhas.joinToString("\n")
        val tudoNorm = Texto.normalizar(tudo)
        if (gatilhos.none { it in tudoNorm }) return null

        // Procura no texto todo (não linha a linha): alguns apps mostram "R$" e "6,05" em nós separados
        val valor = regexValor.findAll(tudo)
            .firstOrNull { it.groups[1] == null && it.groups[3] == null }
            ?.let { Texto.numeroBr(it.groupValues[2]) }
            ?: return null

        // Distâncias e o índice da linha em que aparecem. O formato "5 min (1.7 km)" tem prioridade:
        // assim um endereço como "Rodovia AL-110 km 5" ou "Rua 12 km" não é confundido com a viagem.
        val comTempo = linhas.mapIndexedNotNull { i, l -> distanciaComTempo(l)?.let { i to it } }
        val distancias = comTempo.ifEmpty { linhas.flatMapIndexed { i, l -> distanciasNaLinha(l).map { i to it } } }
        if (distancias.isEmpty()) return null

        val (kmBusca, kmViagem) = if (distancias.size == 1) {
            null to distancias.first().second
        } else {
            distancias.first().second to distancias.last().second
        }

        val iPrimeira = distancias.first().first
        val iUltima = distancias.last().first
        val ehRuido: (String) -> Boolean = { l ->
            val n = Texto.normalizar(l)
            gatilhos.any { it in n } || padroes.ehRotuloRecusar(l) || regexValor.containsMatchIn(l)
        }
        val origem = if (distancias.size > 1) {
            linhas.subList(iPrimeira + 1, iUltima).filterNot(ehRuido).joinToString(" | ")
        } else ""
        val destino = linhas.drop(iUltima + 1).filterNot(ehRuido).joinToString(" | ")
            .ifBlank { tudo } // sem estrutura clara: usa a tela toda

        val emSequencia = padroes.sequencia.any { it in tudoNorm }
        return Oferta(plataforma, valor, kmBusca, kmViagem, origem, destino, tudo, emSequencia)
    }

    private fun distanciaComTempo(linha: String): Double? {
        val m = regexTempoDistancia.find(linha) ?: return null
        val numero = Texto.numeroBr(m.groupValues[1]) ?: return null
        return if (m.groupValues[2].equals("km", ignoreCase = true)) numero else numero / 1000.0
    }

    private fun distanciasNaLinha(linha: String): List<Double> {
        val km = regexKm.findAll(linha).mapNotNull { Texto.numeroBr(it.groupValues[1]) }.toList()
        if (km.isNotEmpty()) return km
        return regexMetros.findAll(linha).mapNotNull { it.groupValues[1].toDoubleOrNull()?.div(1000.0) }.toList()
    }
}
