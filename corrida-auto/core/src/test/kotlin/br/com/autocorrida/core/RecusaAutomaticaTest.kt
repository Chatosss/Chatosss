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

class Tela99Test {
    /** Textos do pop-up da 99 Moto (print real do usuário). */
    private val tela99 = listOf(
        "Moto", "Exclusivo", "R$ 6,05", "Est. de R$1,16/km", "4,94 (951)", "Verificado",
        "3 min (0.7 km)", "Rua Santo Andre, Canafístula, Arapiraca",
        "11 minutos (4.5 km)", "Rua Santo Expedito, 167, Verdes Campos, Arapiraca", "Aceitar",
    )

    @Test
    fun `le o pop-up da 99`() {
        val o = assertNotNull(LeitorDeOferta.ler(tela99, Plataforma.NOVENTA_E_NOVE))
        assertEquals(6.05, o.valor, 0.001)
        assertEquals(0.7, o.kmBusca!!, 0.001)
        assertEquals(4.5, o.kmViagem!!, 0.001)
        assertTrue("Canafístula" in o.origem)
        assertTrue("Verdes Campos" in o.destino)
    }

    @Test
    fun `oferta da 99 a 1,16 por km deve ser recusada`() {
        val r = Regras(minimoPorKm = 1.5)
        val o = LeitorDeOferta.ler(tela99, Plataforma.NOVENTA_E_NOVE)!!
        val d = MotorDeRegras.avaliar(o, r)
        assertIs<Decisao.Recusar>(d)
        assertTrue(MotorDeRegras.deveRecusar(d, r))
    }

    @Test
    fun `valor em nos separados`() {
        val o = assertNotNull(LeitorDeOferta.ler(listOf("R$", "6,05", "1 km", "A", "4 km", "B", "Aceitar"), Plataforma.UBER))
        assertEquals(6.05, o.valor, 0.001)
    }

    @Test
    fun `botao X de fechar`() {
        val p = PadroesAtivos.atual
        assertTrue(p.ehRotuloFechar("Fechar"))
        assertTrue(p.ehRotuloFechar("X"))
        assertTrue(p.ehRotuloFechar("close"))
        assertFalse(p.ehRotuloFechar("Xangai"))
        assertTrue(p.ehIdFechar("com.app99.driver:id/iv_close"))
        assertFalse(p.ehIdFechar("com.app99.driver:id/btn_accept"))
    }

    @Test
    fun `oferta da 99 com Tenho interesse`() {
        val tela = listOf(
            "Moto", "R$ 6,01", "Est. de R$1,16/km", "4,96 (1016)", "Verificado",
            "8 min (4.4 km)", "Av. Vereador João Saturnino de Almeida, Boa Vista, Arapiraca",
            "2 minutos (0.8 km)", "Av. Ver. João Saturnino de Almeida, 843, São Luiz, Arapiraca", "Tenho interesse",
        )
        val o = assertNotNull(LeitorDeOferta.ler(tela, Plataforma.NOVENTA_E_NOVE))
        assertEquals(6.01, o.valor, 0.001)
        assertEquals(4.4, o.kmBusca!!, 0.001)
        assertEquals(0.8, o.kmViagem!!, 0.001)
        assertTrue("São Luiz" in o.destino)
        assertFalse("Tenho interesse" in o.destino)
        val r = Regras(minimoPorKm = 1.5)
        assertTrue(MotorDeRegras.deveRecusar(MotorDeRegras.avaliar(o, r), r))
        assertTrue(PadroesAtivos.atual.ehRotuloAceitar("Tenho interesse"))
    }
}
