package br.com.autocorrida.core

import kotlin.test.*

class RecusaAutomaticaTest {

    private fun oferta(valor: Double, busca: Double?, viagem: Double?, destino: String = "Rua X - Centro") =
        Oferta(Plataforma.UBER, valor, busca, viagem, "Rua Y - Savassi", destino, "")

    private val regras = Regras(minimoPorKm = 1.5)

    private fun recusa(o: Oferta, r: Regras = regras, e: EstadoAtual = EstadoAtual.LIVRE) =
        MotorDeRegras.deveRecusar(MotorDeRegras.avaliar(o, r, e), r)

    @Test
    fun `recusa abaixo do minimo por km`() {
        assertTrue(recusa(oferta(10.0, 2.0, 10.0))) // 0,83/km
    }

    @Test
    fun `nao recusa oferta boa`() {
        assertFalse(recusa(oferta(30.0, 2.0, 10.0)))
    }

    @Test
    fun `nao recusa com a opcao desligada`() {
        assertFalse(recusa(oferta(10.0, 2.0, 10.0), regras.copy(recusarAbaixoPorKm = false)))
    }

    @Test
    fun `nao recusa com aceite automatico desligado`() {
        assertFalse(recusa(oferta(10.0, 2.0, 10.0), regras.copy(ativo = false)))
    }

    @Test
    fun `nao recusa em corrida, so ignora`() {
        assertFalse(recusa(oferta(10.0, 2.0, 10.0), regras, EstadoAtual(Fase.INDO_BUSCAR)))
        assertFalse(recusa(oferta(10.0, 2.0, 10.0), regras, EstadoAtual(Fase.EM_VIAGEM, 3.0, 0)))
    }

    @Test
    fun `recusa no final da corrida se abaixo do minimo`() {
        assertTrue(recusa(oferta(10.0, 2.0, 10.0), regras, EstadoAtual(Fase.EM_VIAGEM, 0.5, 0)))
    }

    @Test
    fun `nao recusa sem conseguir calcular o km`() {
        assertFalse(recusa(oferta(10.0, null, null)))
    }

    @Test
    fun `outros filtros so recusam com a opcao extra`() {
        val r = regras.copy(bairrosPermitidos = listOf("Pampulha"))
        val boa = oferta(30.0, 2.0, 10.0) // R$/km ok, bairro fora
        assertFalse(recusa(boa, r))
        assertTrue(recusa(boa, r.copy(recusarOutrosFiltros = true)))
    }

    @Test
    fun `rotulos de recusar`() {
        val p = PadroesAtivos.atual
        assertTrue(p.ehRotuloRecusar("Recusar"))
        assertTrue(p.ehRotuloRecusar("Não aceitar"))
        assertFalse(p.ehRotuloRecusar("Aceitar"))
        assertFalse(p.ehRotuloAceitar("Não aceitar"))
    }

    @Test
    fun `botao recusar nao entra no destino`() {
        val o = LeitorDeOferta.ler(listOf("R$ 10,00", "2 km", "Bairro A", "10 km", "Bairro B", "Recusar", "Aceitar"), Plataforma.UBER)!!
        assertEquals("Bairro B", o.destino)
    }
}
