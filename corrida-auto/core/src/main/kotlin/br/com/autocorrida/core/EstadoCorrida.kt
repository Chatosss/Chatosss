package br.com.autocorrida.core

/** Em que ponto o motorista está, segundo o que aparece na tela do app de corrida. */
enum class Fase {
    /** Online, sem corrida. */
    LIVRE,
    /** Corrida aceita, indo buscar o passageiro (ou aguardando embarque). */
    INDO_BUSCAR,
    /** Passageiro a bordo, indo para o destino. */
    EM_VIAGEM,
}

/** Uma leitura instantânea da tela de corrida. */
data class LeituraDeEstado(val fase: Fase, val kmRestante: Double?)

/** Estado consolidado, com o horário da última leitura. */
data class EstadoAtual(
    val fase: Fase = Fase.LIVRE,
    /** Km que faltam para o fim da corrida atual (só faz sentido em [Fase.EM_VIAGEM]). */
    val kmRestante: Double? = null,
    val atualizadoEmMs: Long = 0L,
) {
    val emCorrida: Boolean get() = fase != Fase.LIVRE

    companion object {
        val LIVRE = EstadoAtual()
    }
}

/**
 * Lê as telas do app do motorista e identifica se há corrida em andamento
 * e quanto falta para terminar.
 */
object LeitorDeEstado {

    private val marcadoresViagem = listOf(
        "finalizar viagem", "encerrar viagem", "concluir viagem", "finalizar corrida",
        "encerrar corrida", "concluir corrida", "deixar passageiro", "deslize para finalizar",
        "end trip", "complete trip",
    )
    private val marcadoresBusca = listOf(
        "iniciar viagem", "iniciar corrida", "cheguei", "chegou ao local", "aguardando passageiro",
        "buscando passageiro", "a caminho do passageiro", "indo buscar", "start trip", "arrived",
    )
    private val marcadoresLivre = listOf(
        "procurando viagens", "procurando corridas", "voce esta online", "voce esta disponivel",
        "avaliar passageiro", "avalie o passageiro", "corrida finalizada", "viagem finalizada",
        "viagem concluida", "corrida concluida", "finding trips", "you're online",
    )

    private val regexKm = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val regexMetros = Regex("""(?<![\d.,])(\d{1,4})\s*m\b(?!in)""", RegexOption.IGNORE_CASE)
    private val regexManobra = Regex("""\b(vire|siga|em \d|mantenha|pegue|saia|retorno|rotatoria|turn|continue)\b""")

    fun ler(textos: List<String>): LeituraDeEstado? {
        val linhas = textos.map { it.trim() }.filter { it.isNotEmpty() }
        val norm = linhas.map(Texto::normalizar)
        val tudo = norm.joinToString("\n")

        return when {
            marcadoresViagem.any { it in tudo } -> LeituraDeEstado(Fase.EM_VIAGEM, kmRestante(linhas, norm))
            marcadoresBusca.any { it in tudo } -> LeituraDeEstado(Fase.INDO_BUSCAR, null)
            marcadoresLivre.any { it in tudo } -> LeituraDeEstado(Fase.LIVRE, null)
            else -> null
        }
    }

    /**
     * Distância restante até o destino. Prefere a linha de previsão de chegada
     * ("3 min · 0,8 km"), ignorando instruções de manobra ("Em 200 m vire à direita").
     */
    internal fun kmRestante(linhas: List<String>, norm: List<String>): Double? {
        val candidatas = linhas.indices.filterNot { regexManobra.containsMatchIn(norm[it]) }
        val comEta = candidatas.filter { "min" in norm[it] }
        for (i in comEta + candidatas) {
            distancia(linhas[i])?.let { return it }
        }
        return null
    }

    private fun distancia(linha: String): Double? {
        regexKm.find(linha)?.let { return Texto.numeroBr(it.groupValues[1]) }
        regexMetros.find(linha)?.let { return it.groupValues[1].toDoubleOrNull()?.div(1000.0) }
        return null
    }
}

/**
 * Guarda o estado entre os eventos de tela. Leituras ficam "vencidas" depois de
 * [validadeMs]: sem confirmação recente da tela, não confiamos no km restante.
 */
class RastreadorDeCorrida(
    private val validadeMs: Long = 3 * 60_000L,
    private val relogio: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var estado: EstadoAtual = EstadoAtual.LIVRE
        private set

    fun atualizar(leitura: LeituraDeEstado) {
        val anterior = estado
        // Se a tela de viagem não mostrou a distância desta vez, mantém a última conhecida.
        val km = leitura.kmRestante ?: anterior.kmRestante.takeIf {
            leitura.fase == Fase.EM_VIAGEM && anterior.fase == Fase.EM_VIAGEM
        }
        estado = EstadoAtual(leitura.fase, km, relogio())
    }

    /** Estado para usar na decisão. Leitura antiga demais perde o km restante (fica conservador). */
    fun estadoParaDecisao(): EstadoAtual {
        val e = estado
        if (e.fase == Fase.LIVRE) return e
        return if (relogio() - e.atualizadoEmMs > validadeMs) e.copy(kmRestante = null) else e
    }

    fun reiniciar() { estado = EstadoAtual.LIVRE }
}
