package br.com.autocorrida.core

/** Plataformas suportadas, identificadas pelo pacote Android do app do motorista. */
enum class Plataforma(val pacotes: Set<String>) {
    UBER(setOf("com.ubercab.driver")),
    NOVENTA_E_NOVE(setOf("com.app99.driver", "com.taxis99")),
    INDRIVE(setOf("sinet.startup.inDriver")),
    DESCONHECIDA(emptySet());

    companion object {
        fun doPacote(pacote: String?): Plataforma =
            entries.firstOrNull { pacote != null && pacote in it.pacotes } ?: DESCONHECIDA
    }
}

/** Dados extraídos de uma tela de oferta de corrida. */
data class Oferta(
    val plataforma: Plataforma,
    val valor: Double,
    /** Distância até o passageiro (km), se identificada. */
    val kmBusca: Double?,
    /** Distância da viagem em si (km). */
    val kmViagem: Double?,
    /** Texto da origem (embarque). */
    val origem: String,
    /** Texto do destino. */
    val destino: String,
    val textoCompleto: String,
    /**
     * true quando o próprio app informa que a distância de busca já é contada a partir
     * do fim da viagem atual (corrida em sequência, ex.: "após o desembarque").
     */
    val buscaContadaDoFimDaViagem: Boolean = false,
) {
    fun kmTotal(contarBusca: Boolean, kmBuscaUsado: Double? = kmBusca): Double? {
        val viagem = kmViagem ?: return null
        return if (contarBusca) viagem + (kmBuscaUsado ?: 0.0) else viagem
    }

    fun valorPorKm(contarBusca: Boolean, kmBuscaUsado: Double? = kmBusca): Double? {
        val km = kmTotal(contarBusca, kmBuscaUsado) ?: return null
        if (km <= 0.0) return null
        return valor / km
    }
}
