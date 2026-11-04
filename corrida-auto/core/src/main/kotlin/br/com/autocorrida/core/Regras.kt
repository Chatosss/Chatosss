package br.com.autocorrida.core

/** Configuração do motorista. Campos nulos = regra desligada. */
data class Regras(
    val ativo: Boolean = true,
    /** Valor mínimo por km (ex.: 1.5 = R$ 1,50/km). */
    val minimoPorKm: Double? = 1.5,
    val valorMinimo: Double? = null,
    val maxKmBusca: Double? = null,
    val maxKmTotal: Double? = null,
    /** Se true, o km até o passageiro entra no cálculo de R$/km (mais conservador). */
    val contarKmBusca: Boolean = true,
    /** Bairros de destino aceitos. Vazio = qualquer bairro. */
    val bairrosPermitidos: List<String> = emptyList(),
    /** Bairros que nunca devem ser aceitos (origem ou destino). */
    val bairrosBloqueados: List<String> = emptyList(),
    /** Plataformas em que o aceite automático está ligado. */
    val plataformas: Set<Plataforma> = setOf(Plataforma.UBER, Plataforma.NOVENTA_E_NOVE, Plataforma.INDRIVE),

    /** Ignora ofertas enquanto houver corrida em andamento. */
    val ignorarEmCorrida: Boolean = true,
    /** Exceção: aceita (se passar no filtro) quando faltar pouco para terminar a viagem atual. */
    val aceitarNoFinalDaCorrida: Boolean = true,
    /** "Pouco" = até este número de km para o destino da viagem atual. */
    val kmFinalParaAceitar: Double = 1.0,
)

sealed class Decisao {
    abstract val oferta: Oferta

    data class Aceitar(
        override val oferta: Oferta,
        val valorPorKm: Double?,
        /** Km de busca usado no cálculo (a partir do fim da viagem atual, se for o caso). */
        val kmBuscaUsado: Double?,
        val emSequencia: Boolean,
    ) : Decisao()

    data class Recusar(override val oferta: Oferta, val motivos: List<String>) : Decisao()
}

object MotorDeRegras {

    fun avaliar(oferta: Oferta, regras: Regras, estado: EstadoAtual = EstadoAtual.LIVRE): Decisao {
        val motivos = mutableListOf<String>()

        if (!regras.ativo) motivos += "aceite automático desligado"
        if (oferta.plataforma !in regras.plataformas) motivos += "plataforma ${oferta.plataforma} desligada"

        // ---- Corrida em andamento ----
        val finalizando = estado.emCorrida && regras.ignorarEmCorrida && run {
            val restante = estado.kmRestante
            val terminando = regras.aceitarNoFinalDaCorrida && estado.fase == Fase.EM_VIAGEM &&
                restante != null && restante <= regras.kmFinalParaAceitar
            if (!terminando) {
                val motivo = when {
                    estado.fase == Fase.INDO_BUSCAR -> "já em corrida (indo buscar passageiro)"
                    restante == null -> "já em corrida (distância restante desconhecida)"
                    else -> "já em corrida (faltam %.1f km, aceita só com até %.1f km)"
                        .format(restante, regras.kmFinalParaAceitar)
                }
                return Decisao.Recusar(oferta, listOf(motivo))
            }
            true
        }
        val emSequencia = finalizando
        val kmBusca: Double? = if (finalizando) kmBuscaDoFimDaViagem(oferta, estado.kmRestante ?: 0.0) else oferta.kmBusca

        // ---- Filtros de valor e distância ----
        val porKm = oferta.valorPorKm(regras.contarKmBusca, kmBusca)
        regras.minimoPorKm?.let { min ->
            when {
                porKm == null -> motivos += "não foi possível calcular R$/km"
                porKm < min -> motivos += "R$ %.2f/km abaixo do mínimo R$ %.2f/km".format(porKm, min)
            }
        }
        regras.valorMinimo?.let { min ->
            if (oferta.valor < min) motivos += "valor R$ %.2f abaixo do mínimo R$ %.2f".format(oferta.valor, min)
        }
        regras.maxKmBusca?.let { max ->
            if (kmBusca != null && kmBusca > max) motivos += "busca de %.1f km acima de %.1f km".format(kmBusca, max)
        }
        regras.maxKmTotal?.let { max ->
            val total = oferta.kmTotal(regras.contarKmBusca, kmBusca)
            if (total != null && total > max) motivos += "total de %.1f km acima de %.1f km".format(total, max)
        }

        // ---- Bairros ----
        val destinoNorm = Texto.normalizar(oferta.destino)
        val origemNorm = Texto.normalizar(oferta.origem)

        regras.bairrosBloqueados.firstOrNull { b ->
            val n = Texto.normalizar(b)
            n.isNotBlank() && (Texto.contemPalavra(destinoNorm, n) || Texto.contemPalavra(origemNorm, n))
        }?.let { motivos += "bairro bloqueado: $it" }

        if (regras.bairrosPermitidos.isNotEmpty()) {
            val ok = regras.bairrosPermitidos.any { b ->
                val n = Texto.normalizar(b)
                n.isNotBlank() && Texto.contemPalavra(destinoNorm, n)
            }
            if (!ok) motivos += "destino fora dos bairros permitidos"
        }

        return if (motivos.isEmpty()) Decisao.Aceitar(oferta, porKm, kmBusca, emSequencia)
        else Decisao.Recusar(oferta, motivos)
    }

    /**
     * Distância de busca contada a partir do FIM da viagem atual.
     *
     * - Se o app já informa a busca a partir do desembarque (corrida em sequência), usa o valor da tela.
     * - Senão, a tela mostra a distância a partir da posição ATUAL. O trajeto real será
     *   posição atual -> fim da viagem -> passageiro, então a distância fim->passageiro é no
     *   máximo (busca mostrada + km restante). Usamos esse limite superior: nunca superestima
     *   o R$/km, só pode ficar um pouco mais conservador (erro máximo = km restante, ≤ 1 km).
     */
    fun kmBuscaDoFimDaViagem(oferta: Oferta, kmRestante: Double): Double? {
        val busca = oferta.kmBusca ?: return null
        return if (oferta.buscaContadaDoFimDaViagem) busca else busca + kmRestante
    }
}
