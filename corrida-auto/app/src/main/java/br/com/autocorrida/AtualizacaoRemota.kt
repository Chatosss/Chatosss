package br.com.autocorrida

import android.content.Context
import br.com.autocorrida.core.Padroes
import br.com.autocorrida.core.PadroesAtivos
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Baixa o arquivo padroes-remotos.json do GitHub e aplica na hora.
 * O último arquivo válido fica salvo, então o app funciona offline com a última versão.
 */
object AtualizacaoRemota {
    private const val INTERVALO_MS = 5 * 60_000L
    @Volatile private var ultimaTentativa = 0L

    /** Carrega o que estava salvo (chamar ao iniciar o app/serviço). */
    fun carregarCache(context: Context) {
        val json = prefs(context).getString("json", null) ?: return
        runCatching { PadroesAtivos.aplicar(converter(JSONObject(json))) }
    }

    /** Busca a versão nova em segundo plano, no máximo a cada 5 minutos (ou já, se [forcar]). */
    fun verificar(context: Context, forcar: Boolean = false, aoTerminar: ((String) -> Unit)? = null) {
        val agora = System.currentTimeMillis()
        if (!forcar && agora - ultimaTentativa < INTERVALO_MS) return
        ultimaTentativa = agora
        val app = context.applicationContext
        thread(name = "padroes-remotos") {
            val msg = runCatching {
                val texto = baixar(BuildConfig.URL_PADROES + "?t=" + agora)
                val obj = JSONObject(texto)
                val padroes = converter(obj)
                PadroesAtivos.aplicar(padroes)
                prefs(app).edit().putString("json", texto).putLong("em", agora).apply()
                "Padrões atualizados (versão ${padroes.versao})"
            }.getOrElse { "Falha ao atualizar padrões: ${it.message}" }
            aoTerminar?.invoke(msg)
        }
    }

    fun versaoAtual(): Int = PadroesAtivos.atual.versao

    private fun converter(o: JSONObject): Padroes {
        val p = Padroes()
        fun lista(k: String, padrao: List<String>): List<String> {
            val arr: JSONArray = o.optJSONArray(k) ?: return padrao
            return (0 until arr.length()).map { arr.getString(it) }.ifEmpty { padrao }
        }
        return Padroes(
            versao = o.optInt("versao", 0),
            gatilhosOferta = lista("gatilhosOferta", p.gatilhosOferta),
            sequencia = lista("sequencia", p.sequencia),
            rotulosAceitar = lista("rotulosAceitar", p.rotulosAceitar),
            marcadoresViagem = lista("marcadoresViagem", p.marcadoresViagem),
            marcadoresBusca = lista("marcadoresBusca", p.marcadoresBusca),
            marcadoresLivre = lista("marcadoresLivre", p.marcadoresLivre),
            palavrasManobra = lista("palavrasManobra", p.palavrasManobra),
        )
    }

    internal fun baixar(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "corrida-auto")
        return try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences("padroes", Context.MODE_PRIVATE)
}
