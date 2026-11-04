package br.com.autocorrida

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Atualização do próprio APK pelas Releases do GitHub.
 * Cada build do GitHub Actions publica a release "v<número>"; se o número for maior que o
 * versionCode instalado, oferece baixar e instalar (o Android sempre pede confirmação).
 */
object Atualizador {

    data class Versao(val codigo: Int, val nome: String, val urlApk: String, val notas: String)

    fun verificar(activity: Activity, avisarSeAtual: Boolean = false) {
        thread(name = "atualizador") {
            val nova = runCatching { ultimaRelease() }.getOrNull()
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                when {
                    nova == null -> if (avisarSeAtual) mostrar(activity, "Não foi possível verificar atualizações.")
                    nova.codigo > BuildConfig.VERSION_CODE -> perguntar(activity, nova)
                    avisarSeAtual -> mostrar(activity, "Você já está na versão mais recente (${BuildConfig.VERSION_NAME}).")
                }
            }
        }
    }

    private fun ultimaRelease(): Versao? {
        val json = JSONObject(AtualizacaoRemota.baixar(BuildConfig.URL_RELEASES))
        val tag = json.getString("tag_name")
        val codigo = tag.removePrefix("v").toIntOrNull() ?: return null
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        return Versao(codigo, json.optString("name", tag), apk.getString("browser_download_url"), json.optString("body", ""))
    }

    private fun perguntar(a: Activity, v: Versao) {
        AlertDialog.Builder(a)
            .setTitle("Nova versão disponível")
            .setMessage("${v.nome}\n\n${v.notas.take(800)}")
            .setPositiveButton("Atualizar") { _, _ -> baixarEInstalar(a, v) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun baixarEInstalar(a: Activity, v: Versao) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.packageManager.canRequestPackageInstalls()) {
            mostrar(a, "Permita que o Corrida Auto instale apps e toque em Atualizar de novo.")
            a.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))
            )
            return
        }
        mostrar(a, "Baixando atualização…")
        thread(name = "baixar-apk") {
            val arquivo = File(a.cacheDir, "atualizacoes/corrida-auto.apk")
            val ok = runCatching {
                arquivo.parentFile?.mkdirs()
                val c = abrirSeguindoRedirecionamentos(v.urlApk)
                try {
                    if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                    c.inputStream.use { entrada -> arquivo.outputStream().use { entrada.copyTo(it) } }
                } finally {
                    c.disconnect()
                }
            }.isSuccess
            a.runOnUiThread {
                if (!ok) { mostrar(a, "Falha ao baixar a atualização."); return@runOnUiThread }
                val uri = FileProvider.getUriForFile(a, "${a.packageName}.arquivos", arquivo)
                a.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** O GitHub redireciona o download do APK para outro domínio. */
    private fun abrirSeguindoRedirecionamentos(inicio: String): HttpURLConnection {
        var url = URL(inicio)
        repeat(5) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "corrida-auto")
            val destino = c.getHeaderField("Location")
            if (c.responseCode !in 300..399 || destino == null) return c
            c.disconnect()
            url = URL(url, destino)
        }
        error("redirecionamentos demais")
    }

    private fun mostrar(a: Activity, msg: String) =
        android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_LONG).show()
}
