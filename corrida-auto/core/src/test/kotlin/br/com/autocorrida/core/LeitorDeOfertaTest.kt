package br.com.autocorrida.core

import kotlin.test.*

class LeitorDeOfertaTest {

    private val telaUber = listOf(
        "UberX",
        "R$ 18,40",
        "4,9 ★ (230)",
        "5 min (1,8 km) de distância",
        "Rua das Flores, 120 - Centro",
        "Viagem de 22 min (10,2 km)",
        "Av. Brasil, 900 - Jardim América",
        "Aceitar",
    )

    @Test
    fun `le oferta no layout da Uber`() {
        val o = assertNotNull(LeitorDeOferta.ler(telaUber, Plataforma.UBER))
        assertEquals(18.40, o.valor, 0.001)
        assertEquals(1.8, o.kmBusca!!, 0.001)
        assertEquals(10.2, o.kmViagem!!, 0.001)
        assertTrue("Centro" in o.origem)
        assertTrue("Jardim América" in o.destino)
        assertFalse("Aceitar" in o.destino)
        assertEquals(18.40 / 12.0, o.valorPorKm(true)!!, 0.001)
    }

    @Test
    fun `ignora adicional e valor por km ao achar o valor`() {
        val tela = listOf("+R$ 3,00 dinâmico", "R$ 1,20/km", "R$ 25,00", "800 m", "Bairro A", "12 km", "Bairro B", "Toque para aceitar")
        val o = assertNotNull(LeitorDeOferta.ler(tela, Plataforma.NOVENTA_E_NOVE))
        assertEquals(25.0, o.valor, 0.001)
        assertEquals(0.8, o.kmBusca!!, 0.001)
        assertEquals(12.0, o.kmViagem!!, 0.001)
        assertEquals("Bairro A", o.origem)
        assertEquals("Bairro B", o.destino)
    }

    @Test
    fun `tela sem botao de aceitar nao e oferta`() {
        assertNull(LeitorDeOferta.ler(listOf("R$ 18,40", "10 km", "Ganhos de hoje"), Plataforma.UBER))
    }

    @Test
    fun `numeros com milhar`() {
        val o = assertNotNull(LeitorDeOferta.ler(listOf("R$ 1.050,00", "350 km", "Aceitar"), Plataforma.UBER))
        assertEquals(1050.0, o.valor, 0.001)
        assertNull(o.kmBusca)
        assertEquals(350.0, o.kmViagem!!, 0.001)
    }
}
