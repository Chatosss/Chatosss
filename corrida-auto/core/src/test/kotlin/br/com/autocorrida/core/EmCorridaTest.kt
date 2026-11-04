package br.com.autocorrida.core

import kotlin.test.*

class EmCorridaTest {

    private fun oferta(valor: Double, busca: Double, viagem: Double, sequencia: Boolean = false) =
        Oferta(Plataforma.UBER, valor, busca, viagem, "Origem - Centro", "Destino - Centro", "", sequencia)

    private val regras = Regras(minimoPorKm = 1.5)
    private fun emViagem(km: Double?) = EstadoAtual(Fase.EM_VIAGEM, km, 0)

    @Test
    fun `ignora oferta indo buscar passageiro`() {
        val d = MotorDeRegras.avaliar(oferta(100.0, 1.0, 10.0), regras, EstadoAtual(Fase.INDO_BUSCAR))
        assertIs<Decisao.Recusar>(d)
        assertTrue(d.motivos.single().startsWith("já em corrida"))
    }

    @Test
    fun `ignora oferta em viagem longe do fim`() {
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(100.0, 1.0, 10.0), regras, emViagem(3.5)))
    }

    @Test
    fun `ignora se nao sabe quanto falta`() {
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(100.0, 1.0, 10.0), regras, emViagem(null)))
    }

    @Test
    fun `aceita no final da corrida contando a busca a partir do fim`() {
        // busca 2 km (da posição atual) + 0,8 km restante = 2,8 km a partir do fim
        // total = 2,8 + 10 = 12,8 km -> R$ 20 / 12,8 = 1,5625/km  -> aceita
        val d = MotorDeRegras.avaliar(oferta(20.0, 2.0, 10.0), regras, emViagem(0.8))
        assertIs<Decisao.Aceitar>(d)
        assertTrue(d.emSequencia)
        assertEquals(2.8, d.kmBuscaUsado!!, 0.001)
        assertEquals(20.0 / 12.8, d.valorPorKm!!, 0.001)
    }

    @Test
    fun `no final da corrida ainda aplica o filtro de R$ por km`() {
        // 19 / 12,8 = 1,48/km -> recusa
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(19.0, 2.0, 10.0), regras, emViagem(0.8)))
    }

    @Test
    fun `oferta em sequencia ja vem medida do desembarque`() {
        val d = MotorDeRegras.avaliar(oferta(18.0, 2.0, 10.0, sequencia = true), regras, emViagem(0.5))
        assertIs<Decisao.Aceitar>(d)
        assertEquals(2.0, d.kmBuscaUsado!!, 0.001)
    }

    @Test
    fun `limite de km final configuravel`() {
        val r = regras.copy(kmFinalParaAceitar = 0.5)
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(100.0, 1.0, 10.0), r, emViagem(0.8)))
    }

    @Test
    fun `com ignorarEmCorrida desligado avalia normalmente`() {
        val r = regras.copy(ignorarEmCorrida = false)
        val d = MotorDeRegras.avaliar(oferta(18.0, 2.0, 10.0), r, emViagem(5.0))
        assertIs<Decisao.Aceitar>(d)
        assertEquals(2.0, d.kmBuscaUsado!!, 0.001)
    }

    @Test
    fun `le estado das telas`() {
        val viagem = LeitorDeEstado.ler(listOf("Em 200 m vire à direita", "Rua X", "3 min · 0,8 km", "Finalizar viagem"))!!
        assertEquals(Fase.EM_VIAGEM, viagem.fase)
        assertEquals(0.8, viagem.kmRestante!!, 0.001)

        val metros = LeitorDeEstado.ler(listOf("2 min", "650 m", "Deslize para finalizar"))!!
        assertEquals(0.65, metros.kmRestante!!, 0.001)

        assertEquals(Fase.INDO_BUSCAR, LeitorDeEstado.ler(listOf("João", "Iniciar viagem"))!!.fase)
        assertEquals(Fase.LIVRE, LeitorDeEstado.ler(listOf("Procurando viagens"))!!.fase)
        assertNull(LeitorDeEstado.ler(listOf("Ganhos da semana")))
    }

    @Test
    fun `rastreador mantem km e expira leitura antiga`() {
        var agora = 0L
        val r = RastreadorDeCorrida(validadeMs = 1000, relogio = { agora })
        r.atualizar(LeituraDeEstado(Fase.EM_VIAGEM, 0.9))
        r.atualizar(LeituraDeEstado(Fase.EM_VIAGEM, null))
        assertEquals(0.9, r.estadoParaDecisao().kmRestante!!, 0.001)
        agora = 5000
        assertNull(r.estadoParaDecisao().kmRestante)
        assertEquals(Fase.EM_VIAGEM, r.estadoParaDecisao().fase)
        r.atualizar(LeituraDeEstado(Fase.LIVRE, null))
        assertFalse(r.estadoParaDecisao().emCorrida)
    }

    @Test
    fun `detecta oferta em sequencia na tela`() {
        val o = LeitorDeOferta.ler(
            listOf("Próxima viagem", "R$ 15,00", "1,5 km após o desembarque", "Bairro A", "8 km", "Bairro B", "Aceitar"),
            Plataforma.UBER,
        )!!
        assertTrue(o.buscaContadaDoFimDaViagem)
    }
}
