package br.com.autocorrida

import android.content.Context
import br.com.autocorrida.core.Plataforma
import br.com.autocorrida.core.Regras

/** Persiste as [Regras] em SharedPreferences. */
class Configuracoes(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

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
            .apply()
    }

    private fun android.content.SharedPreferences.getNum(k: String, padrao: Double?): Double? =
        if (contains(k)) getString(k, null)?.toDoubleOrNull() else padrao

    private fun android.content.SharedPreferences.Editor.putNum(k: String, v: Double?) =
        putString(k, v?.toString() ?: "")

    private fun String.linhas() = split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
