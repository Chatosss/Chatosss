package br.com.autocorrida.core

import kotlin.test.*

class MotorDeRegrasTest {

    private fun oferta(valor: Double, busca: Double?, viagem: Double, destino: String = "Rua X - Centro", origem: String = "Rua Y - Savassi") =
        Oferta(Plataforma.UBER, valor, busca, viagem, origem, destino, "")

    @Test
    fun `aceita acima de 1,50 por km`() {
        val d = MotorDeRegras.avaliar(oferta(20.0, 2.0, 10.0), Regras(minimoPorKm = 1.5))
        assertIs<Decisao.Aceitar>(d)
        assertEquals(20.0 / 12.0, d.valorPorKm!!, 0.001)
    }

    @Test
    fun `recusa abaixo de 1,50 por km contando a busca`() {
        val d = MotorDeRegras.avaliar(oferta(16.0, 2.0, 10.0), Regras(minimoPorKm = 1.5))
        assertIs<Decisao.Recusar>(d)
    }

    @Test
    fun `sem contar a busca a mesma oferta passa`() {
        val d = MotorDeRegras.avaliar(oferta(16.0, 2.0, 10.0), Regras(minimoPorKm = 1.5, contarKmBusca = false))
        assertIs<Decisao.Aceitar>(d)
    }

    @Test
    fun `filtra por bairro de destino ignorando acento e caixa`() {
        val regras = Regras(bairrosPermitidos = listOf("jardim america", "Centro"))
        assertIs<Decisao.Aceitar>(MotorDeRegras.avaliar(oferta(30.0, 1.0, 10.0, destino = "Av. Brasil - Jardim América"), regras))
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(30.0, 1.0, 10.0, destino = "Rua Z - Pampulha"), regras))
    }

    @Test
    fun `bairro permitido precisa ser palavra inteira`() {
        val regras = Regras(bairrosPermitidos = listOf("Centro"))
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(30.0, 1.0, 10.0, destino = "Shopping Centrol"), regras))
    }

    @Test
    fun `bairro bloqueado na origem ou destino recusa`() {
        val regras = Regras(bairrosBloqueados = listOf("Savassi"))
        val d = MotorDeRegras.avaliar(oferta(50.0, 1.0, 10.0), regras)
        assertIs<Decisao.Recusar>(d)
        assertTrue(d.motivos.any { "Savassi" in it })
    }

    @Test
    fun `limites de busca, total e valor minimo`() {
        val regras = Regras(minimoPorKm = null, maxKmBusca = 3.0, maxKmTotal = 20.0, valorMinimo = 10.0)
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(30.0, 4.0, 10.0), regras))
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(30.0, 1.0, 25.0), regras))
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(8.0, 1.0, 3.0), regras))
        assertIs<Decisao.Aceitar>(MotorDeRegras.avaliar(oferta(30.0, 1.0, 10.0), regras))
    }

    @Test
    fun `desligado nunca aceita`() {
        assertIs<Decisao.Recusar>(MotorDeRegras.avaliar(oferta(100.0, 1.0, 10.0), Regras(ativo = false)))
    }
}
