package br.com.autocorrida

import android.content.Context
import br.com.autocorrida.core.Plataforma
import br.com.autocorrida.core.Regras

/** Persiste as [Regras] em SharedPreferences. */
class Configuracoes(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    /** Liga/desliga só o aceite automático, sem mexer nos outros filtros (usado pela bolinha). */
    var ativo: Boolean
        get() = prefs.getBoolean("ativo", false)
        set(v) = prefs.edit().putBoolean("ativo", v).apply()

    var mostrarBolinha: Boolean
        get() = prefs.getBoolean("mostrarBolinha", true)
        set(v) = prefs.edit().putBoolean("mostrarBolinha", v).apply()

    /** Posição salva da bolinha (x, y em pixels); -1 = posição padrão. */
    var posicaoBolinha: Pair<Int, Int>
        get() = prefs.getInt("bolinhaX", -1) to prefs.getInt("bolinhaY", -1)
        set(v) = prefs.edit().putInt("bolinhaX", v.first).putInt("bolinhaY", v.second).apply()

    fun observar(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun pararDeObservar(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    var atrasoMs: Long
        get() = prefs.getLong("atrasoMs", 600L)
        set(v) = prefs.edit().putLong("atrasoMs", v).apply()

    fun carregar(): Regras = Regras(
        ativo = prefs.getBoolean("ativo", false),
        minimoPorKm = prefs.getNum("minimoPorKm", 1.5),
        valorMinimo = prefs.getNum("valorMinimo", null),
        maxKmBusca = prefs.getNum("maxKmBusca", null),
        maxKmTotal = prefs.getNum("maxKmTotal", null),
        contarKmBusca = prefs.getBoolean("contarKmBusca", true),
        bairrosPermitidos = prefs.getString("bairrosPermitidos", "")!!.linhas(),
        bairrosBloqueados = prefs.getString("bairrosBloqueados", "")!!.linhas(),
        plataformas = prefs.getStringSet("plataformas", null)
            ?.mapNotNull { runCatching { Plataforma.valueOf(it) }.getOrNull() }?.toSet()
            ?: Regras().plataformas,
        ignorarEmCorrida = prefs.getBoolean("ignorarEmCorrida", true),
        aceitarNoFinalDaCorrida = prefs.getBoolean("aceitarNoFinalDaCorrida", true),
        kmFinalParaAceitar = prefs.getNum("kmFinalParaAceitar", 1.0) ?: 1.0,
        recusarAbaixoPorKm = prefs.getBoolean("recusarAbaixoPorKm", true),
        recusarOutrosFiltros = prefs.getBoolean("recusarOutrosFiltros", false),
    )

    fun salvar(r: Regras) {
        prefs.edit()
            .putBoolean("ativo", r.ativo)
            .putNum("minimoPorKm", r.minimoPorKm)
            .putNum("valorMinimo", r.valorMinimo)
            .putNum("maxKmBusca", r.maxKmBusca)
            .putNum("maxKmTotal", r.maxKmTotal)
            .putBoolean("contarKmBusca", r.contarKmBusca)
            .putString("bairrosPermitidos", r.bairrosPermitidos.joinToString("\n"))
            .putString("bairrosBloqueados", r.bairrosBloqueados.joinToString("\n"))
            .putStringSet("plataformas", r.plataformas.map { it.name }.toSet())
            .putBoolean("ignorarEmCorrida", r.ignorarEmCorrida)
            .putBoolean("aceitarNoFinalDaCorrida", r.aceitarNoFinalDaCorrida)
            .putNum("kmFinalParaAceitar", r.kmFinalParaAceitar)
            .putBoolean("recusarAbaixoPorKm", r.recusarAbaixoPorKm)
            .putBoolean("recusarOutrosFiltros", r.recusarOutrosFiltros)
            .apply()
    }

    private fun android.content.SharedPreferences.getNum(k: String, padrao: Double?): Double? =
        if (contains(k)) getString(k, null)?.toDoubleOrNull() else padrao

    private fun android.content.SharedPreferences.Editor.putNum(k: String, v: Double?) =
        putString(k, v?.toString() ?: "")

    private fun String.linhas() = split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
