package br.com.autocorrida.core

import kotlin.test.*

class PadroesTest {

    @Test
    fun `padroes personalizados mudam a leitura`() {
        val p = Padroes(gatilhosOferta = listOf("Nova Viagem Disponível")).normalizado()
        val tela = listOf("Nova viagem disponível", "R$ 20,00", "1 km", "A", "10 km", "B")
        assertNull(LeitorDeOferta.ler(tela, Plataforma.UBER))
        assertNotNull(LeitorDeOferta.ler(tela, Plataforma.UBER, p))
    }

    @Test
    fun `marcadores de estado personalizados`() {
        val p = Padroes(marcadoresViagem = listOf("Encerrar Trajeto")).normalizado()
        assertEquals(Fase.EM_VIAGEM, LeitorDeEstado.ler(listOf("0,5 km", "Encerrar trajeto"), p)!!.fase)
    }

    @Test
    fun `rotulo aceitar ignora acento e caixa`() {
        val p = Padroes(rotulosAceitar = listOf("aceitar", "aceitar já")).normalizado()
        assertTrue(p.ehRotuloAceitar("ACEITAR"))
        assertTrue(p.ehRotuloAceitar("Aceitar por R$ 12,00"))
        assertTrue(p.ehRotuloAceitar("Aceitar Ja"))
        assertFalse(p.ehRotuloAceitar("Não aceitar"))
    }
}
