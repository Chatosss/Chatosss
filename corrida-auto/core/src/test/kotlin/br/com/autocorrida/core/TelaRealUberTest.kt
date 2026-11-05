package br.com.autocorrida.core

import kotlin.test.*

/**
 * Oferta real do Uber Driver, capturada por `uiautomator dump` em um celular de 1080x2408.
 * Ordem, classes e posições são as da tela; os endereços e a nota foram trocados por fictícios.
 */
class TelaRealUberTest {

    private fun lim(e: Int, t: Int, d: Int, b: Int) = Limites(e, t, d, b)

    private val tela = listOf(
        NoDaTela(clicavel = true, limites = lim(0, 0, 1080, 2408)), // mapa
        NoDaTela(id = "map_marker", clicavel = true, limites = lim(108, 116, 209, 299)),
        NoDaTela(id = "map_marker", clicavel = true, limites = lim(697, 755, 798, 938)),
        NoDaTela(clicavel = true, limites = lim(45, 978, 1035, 2228)), // cartão
        NoDaTela(texto = "Moto", limites = lim(181, 1023, 314, 1114)),
        NoDaTela(texto = "Exclusivo", limites = lim(337, 1023, 583, 1114)),
        NoDaTela(texto = "R$ 7,06", limites = lim(90, 1114, 491, 1279)),
        NoDaTela(texto = "Est. de R$1,26/km", limites = lim(90, 1279, 544, 1356)),
        NoDaTela(texto = "4,80 (100)", limites = lim(181, 1356, 413, 1447)),
        NoDaTela(texto = "Verificado", limites = lim(527, 1356, 768, 1447)),
        NoDaTela(texto = "5 min (1.7 km)", limites = lim(181, 1518, 686, 1617)),
        NoDaTela(texto = "Rua das Palmeiras, Centro, Cidade Exemplo", limites = lim(181, 1617, 990, 1749)),
        NoDaTela(texto = "9 minutos (3.9 km)", limites = lim(181, 1749, 850, 1848)),
        NoDaTela(texto = "R. das Acácias, 100, Jardim Exemplo, Cidade Exemplo", limites = lim(181, 1848, 990, 1980)),
        NoDaTela(texto = "Aceitar", clicavel = true, limites = lim(90, 2026, 990, 2183)),
        NoDaTela(clicavel = true, limites = lim(855, 1023, 990, 1158)), // o X
    )
    private val indiceDoX = tela.lastIndex
    private val textos = tela.mapNotNull { it.texto ?: it.descricao }

    @Test
    fun `le valor, distancias e enderecos`() {
        val o = assertNotNull(LeitorDeOferta.ler(textos, Plataforma.UBER))
        assertEquals(7.06, o.valor, 0.001)
        assertEquals(1.7, o.kmBusca!!, 0.001)
        assertEquals(3.9, o.kmViagem!!, 0.001)
        assertEquals("Rua das Palmeiras, Centro, Cidade Exemplo", o.origem)
        assertEquals("R. das Acácias, 100, Jardim Exemplo, Cidade Exemplo", o.destino)
    }

    @Test
    fun `calcula o mesmo R$ por km que o Uber mostra e recusa abaixo do minimo`() {
        val o = assertNotNull(LeitorDeOferta.ler(textos, Plataforma.UBER))
        assertEquals(1.26, o.valorPorKm(true)!!, 0.005)
        val regras = Regras()
        val d = MotorDeRegras.avaliar(o, regras)
        assertIs<Decisao.Recusar>(d)
        assertTrue(MotorDeRegras.deveRecusar(d, regras))
    }

    @Test
    fun `com o aceite desligado le mas nao toca em nada`() {
        val o = assertNotNull(LeitorDeOferta.ler(textos, Plataforma.UBER))
        val regras = Regras(ativo = false)
        assertFalse(MotorDeRegras.deveRecusar(MotorDeRegras.avaliar(o, regras), regras))
    }

    @Test
    fun `endereco com km nao vira distancia da viagem`() {
        val comRodovia = textos.toMutableList().apply {
            this[lastIndexOf("R. das Acácias, 100, Jardim Exemplo, Cidade Exemplo")] = "Rodovia AL-110 km 5, 110 km, Cidade Exemplo"
        }
        val o = assertNotNull(LeitorDeOferta.ler(comRodovia, Plataforma.UBER))
        assertEquals(3.9, o.kmViagem!!, 0.001)
    }

    @Test
    fun `acha o cartao da oferta`() {
        assertEquals(lim(45, 978, 1035, 2228), LocalizadorDeRecusa.acharCartao(tela))
    }

    @Test
    fun `acha o X do cartao`() {
        val r = assertNotNull(LocalizadorDeRecusa.localizar(tela, 1080, 2408))
        assertEquals(indiceDoX, r.indice)
    }

    @Test
    fun `pino do mapa mais a direita que o X nao e escolhido`() {
        val pino = NoDaTela(id = "map_marker", clicavel = true, limites = lim(960, 900, 1061, 1083))
        val r = assertNotNull(LocalizadorDeRecusa.localizar(tela + pino, 1080, 2408))
        assertEquals(indiceDoX, r.indice)
    }

    @Test
    fun `botao fechar fora do cartao nao e usado`() {
        val fora = NoDaTela(descricao = "Fechar", clicavel = true, limites = lim(900, 65, 1035, 200))
        val r = assertNotNull(LocalizadorDeRecusa.localizar(listOf(fora) + tela, 1080, 2408))
        assertEquals(indiceDoX + 1, r.indice)
    }

    @Test
    fun `sem cartao identificado nao toca em nada pela posicao`() {
        val soMapa = tela.filter { it.id == "map_marker" }
        assertNull(LocalizadorDeRecusa.localizar(soMapa, 1080, 2408))
    }

    @Test
    fun `tela inicial do Uber nao e oferta`() {
        val inicial = listOf(
            "Página inicial", "Pesquisar locais", "Procurando viagens",
            "O Passe de ganhos de R$ 780 está ativo", "Expira em 3 de mai., 06:54", "+R$ 3", "1-8 min",
        )
        assertNull(LeitorDeOferta.ler(inicial, Plataforma.UBER))
    }
}
