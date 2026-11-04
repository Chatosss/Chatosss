package br.com.autocorrida.core

/**
 * Textos que o app procura na tela. Ficam fora do código para poderem ser
 * atualizados remotamente (arquivo padroes-remotos.json no repositório),
 * sem reinstalar o app. Todos são comparados sem acento e em minúsculas.
 */
data class Padroes(
    val versao: Int = 0,
    /** Indicam que a tela é uma oferta de corrida. */
    val gatilhosOferta: List<String> = listOf("aceitar", "accept", "toque para aceitar", "nova corrida", "nova solicitacao"),
    /** Oferta em sequência: busca contada a partir do desembarque. */
    val sequencia: List<String> = listOf(
        "apos o desembarque", "apos a viagem atual", "depois desta viagem", "apos esta viagem",
        "apos deixar", "proxima viagem", "viagem em sequencia", "corrida em sequencia", "after this trip",
    ),
    /** Rótulos do botão que aceita a corrida. */
    val rotulosAceitar: List<String> = listOf(
        "aceitar", "aceitar corrida", "aceitar viagem", "toque para aceitar", "accept", "aceitar por",
    ),
    /** Rótulos do botão que recusa a corrida. */
    val rotulosRecusar: List<String> = listOf(
        "recusar", "rejeitar", "ignorar", "dispensar", "nao aceitar", "pular", "decline", "reject",
    ),
    val marcadoresViagem: List<String> = listOf(
        "finalizar viagem", "encerrar viagem", "concluir viagem", "finalizar corrida",
        "encerrar corrida", "concluir corrida", "deixar passageiro", "deslize para finalizar",
        "end trip", "complete trip",
    ),
    val marcadoresBusca: List<String> = listOf(
        "iniciar viagem", "iniciar corrida", "cheguei", "chegou ao local", "aguardando passageiro",
        "buscando passageiro", "a caminho do passageiro", "indo buscar", "start trip", "arrived",
    ),
    val marcadoresLivre: List<String> = listOf(
        "procurando viagens", "procurando corridas", "voce esta online", "voce esta disponivel",
        "avaliar passageiro", "avalie o passageiro", "corrida finalizada", "viagem finalizada",
        "viagem concluida", "corrida concluida", "finding trips", "you're online",
    ),
    /** Palavras de instrução de navegação, para não confundir "em 200 m vire" com km restante. */
    val palavrasManobra: List<String> = listOf(
        "vire", "siga", "mantenha", "pegue", "saia", "retorno", "rotatoria", "turn", "continue",
    ),
) {
    /** true se [texto] (de um botão na tela) for um dos rótulos de aceitar. Use em padrões já normalizados. */
    fun ehRotuloAceitar(texto: String): Boolean {
        val t = Texto.normalizar(texto)
        return rotulosAceitar.any { t == it || t.startsWith("$it ") }
    }

    /** true se [texto] (de um botão na tela) for um dos rótulos de recusar. */
    fun ehRotuloRecusar(texto: String): Boolean {
        val t = Texto.normalizar(texto)
        return rotulosRecusar.any { t == it || t.startsWith("$it ") }
    }

    /** Cópia com todos os textos normalizados (sem acento, minúsculos). */
    fun normalizado(): Padroes {
        fun List<String>.n() = map(Texto::normalizar).filter { it.isNotEmpty() }.distinct()
        return copy(
            gatilhosOferta = gatilhosOferta.n(), sequencia = sequencia.n(), rotulosAceitar = rotulosAceitar.n(), rotulosRecusar = rotulosRecusar.n(),
            marcadoresViagem = marcadoresViagem.n(), marcadoresBusca = marcadoresBusca.n(),
            marcadoresLivre = marcadoresLivre.n(), palavrasManobra = palavrasManobra.n(),
        )
    }
}

/** Padrões em uso no momento (trocados quando chega uma atualização remota). */
object PadroesAtivos {
    @Volatile
    var atual: Padroes = Padroes().normalizado()
        private set

    fun aplicar(p: Padroes) {
        atual = p.normalizado()
    }
}
