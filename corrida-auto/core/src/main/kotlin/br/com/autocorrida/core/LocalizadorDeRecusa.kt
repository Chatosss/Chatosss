package br.com.autocorrida.core

/** Retângulo de um elemento na tela, em pixels. */
data class Limites(val esquerda: Int, val topo: Int, val direita: Int, val base: Int) {
    val largura get() = direita - esquerda
    val altura get() = base - topo
    val centroX get() = (esquerda + direita) / 2
    val centroY get() = (topo + base) / 2
    val vazio get() = largura <= 0 || altura <= 0

    fun contem(o: Limites) = esquerda <= o.esquerda && topo <= o.topo && direita >= o.direita && base >= o.base
    fun contemCentroDe(o: Limites) = o.centroX in esquerda..direita && o.centroY in topo..base
}

/** Um elemento da tela, sem depender de classes do Android (para poder ser testado). */
data class NoDaTela(
    val texto: String? = null,
    val descricao: String? = null,
    val id: String? = null,
    val clicavel: Boolean = false,
    val limites: Limites,
)

/**
 * Decide em qual elemento tocar para recusar uma oferta.
 *
 * A busca fica restrita ao cartão da oferta: o mapa atrás dele tem pinos clicáveis sem texto, e tocar
 * em um deles não recusaria nada.
 */
object LocalizadorDeRecusa {

    data class Resultado(val indice: Int, val como: String)

    /** Cartão = menor elemento que contém o valor da oferta e o botão de aceitar. */
    fun acharCartao(nos: List<NoDaTela>, padroes: Padroes = PadroesAtivos.atual): Limites? {
        val valor = acharValor(nos) ?: return null
        val aceitar = nos.firstOrNull { n ->
            !n.limites.vazio && listOfNotNull(n.texto, n.descricao).any(padroes::ehRotuloAceitar)
        }?.limites
        return nos.asSequence()
            .map { it.limites }
            .filter { !it.vazio && it != valor && it.contem(valor) && (aceitar == null || it.contem(aceitar)) }
            .minByOrNull { it.largura.toLong() * it.altura }
    }

    fun localizar(nos: List<NoDaTela>, larguraTela: Int, alturaTela: Int, padroes: Padroes = PadroesAtivos.atual): Resultado? {
        val cartao = acharCartao(nos, padroes)
        fun noCartao(n: NoDaTela) = !n.limites.vazio && (cartao == null || cartao.contemCentroDe(n.limites))

        val porTexto = nos.indexOfFirst { n ->
            noCartao(n) && listOfNotNull(n.texto, n.descricao).any(padroes::ehRotuloRecusar)
        }
        if (porTexto >= 0) return Resultado(porTexto, "texto \"${rotulo(nos[porTexto])}\"")

        val porFechar = nos.indexOfFirst { n ->
            noCartao(n) && (listOfNotNull(n.texto, n.descricao).any(padroes::ehRotuloFechar) || (n.id != null && padroes.ehIdFechar(n.id)))
        }
        if (porFechar >= 0) return Resultado(porFechar, "fechar \"${rotulo(nos[porFechar])}\"")

        // Sem cartão identificado não há como distinguir o X de um pino do mapa: melhor não tocar.
        if (cartao == null) return null
        val valor = acharValor(nos) ?: return null
        val indice = nos.indices
            .filter { i ->
                val n = nos[i]
                val r = n.limites
                n.clicavel && n.texto == null && !r.vazio && cartao.contem(r) && r != cartao &&
                    r.largura < larguraTela * 0.3f && r.altura < alturaTela * 0.12f &&
                    r.centroX > cartao.centroX && r.centroY < valor.base
            }
            .maxWithOrNull(compareBy<Int> { nos[it].limites.centroX }.thenBy { -nos[it].limites.centroY })
            ?: return null
        val r = nos[indice].limites
        return Resultado(indice, "X pela posição @${r.esquerda},${r.topo}-${r.direita},${r.base}")
    }

    /** O valor da oferta: de preferência um elemento só com "R$ 7,06", sem frases como "Passe de R$ 780". */
    private fun acharValor(nos: List<NoDaTela>): Limites? {
        fun candidatos() = nos.asSequence().filter { !it.limites.vazio }.mapNotNull { n ->
            (n.texto ?: n.descricao)?.trim()?.let { it to n.limites }
        }
        return candidatos().firstOrNull { (t, _) -> soValor.matches(t) }?.second
            ?: candidatos().firstOrNull { (t, _) -> t.contains("R$") && !t.contains("/km") && !t.contains("/min") && !t.contains("+R$") }?.second
    }

    private fun rotulo(n: NoDaTela) = n.texto ?: n.descricao ?: n.id ?: "?"

    private val soValor = Regex("""R\$\s*[\d.,]+""")
}
