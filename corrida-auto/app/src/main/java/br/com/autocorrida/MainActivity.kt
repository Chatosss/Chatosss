package br.com.autocorrida

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.autocorrida.core.Fase
import br.com.autocorrida.core.Plataforma
import br.com.autocorrida.core.Regras

class MainActivity : AppCompatActivity() {

    private lateinit var config: Configuracoes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        config = Configuracoes(this)
        preencher(config.carregar())
        AtualizacaoRemota.carregarCache(this)

        findViewById<Button>(R.id.btnAtualizar).setOnClickListener {
            AtualizacaoRemota.verificar(this, forcar = true) { msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    atualizarStatus()
                }
            }
            Atualizador.verificar(this, avisarSeAtual = true)
        }
        Atualizador.verificar(this)

        findViewById<Button>(R.id.btnAcessibilidade).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnSalvar).setOnClickListener {
            config.salvar(lerTela())
            config.atrasoMs = num(R.id.edAtraso)?.toLong() ?: 600L
            Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Historico.aoMudar = { runOnUiThread { atualizarStatus() } }
        atualizarStatus()
    }

    override fun onPause() {
        super.onPause()
        Historico.aoMudar = null
    }

    private fun atualizarStatus() {
        val e = Historico.estado
        val fase = when (e.fase) {
            Fase.LIVRE -> "Livre"
            Fase.INDO_BUSCAR -> "Indo buscar passageiro"
            Fase.EM_VIAGEM -> "Em viagem" + (e.kmRestante?.let { " – faltam %.1f km".format(it) } ?: "")
        }
        findViewById<TextView>(R.id.txtStatus).text = "Estado: $fase"
        findViewById<TextView>(R.id.txtVersao).text =
            "App ${BuildConfig.VERSION_NAME} · padrões de leitura v${AtualizacaoRemota.versaoAtual()}"
        findViewById<TextView>(R.id.txtHistorico).text = Historico.texto().ifEmpty { "Nenhuma oferta ainda." }
    }

    private fun preencher(r: Regras) {
        sw(R.id.swAtivo).isChecked = r.ativo
        txt(R.id.edMinKm, r.minimoPorKm)
        sw(R.id.swContarBusca).isChecked = r.contarKmBusca
        txt(R.id.edValorMin, r.valorMinimo)
        txt(R.id.edMaxBusca, r.maxKmBusca)
        txt(R.id.edMaxTotal, r.maxKmTotal)
        findViewById<EditText>(R.id.edPermitidos).setText(r.bairrosPermitidos.joinToString("\n"))
        findViewById<EditText>(R.id.edBloqueados).setText(r.bairrosBloqueados.joinToString("\n"))
        sw(R.id.swIgnorarEmCorrida).isChecked = r.ignorarEmCorrida
        sw(R.id.swAceitarNoFinal).isChecked = r.aceitarNoFinalDaCorrida
        txt(R.id.edKmFinal, r.kmFinalParaAceitar)
        cb(R.id.cbUber).isChecked = Plataforma.UBER in r.plataformas
        cb(R.id.cb99).isChecked = Plataforma.NOVENTA_E_NOVE in r.plataformas
        cb(R.id.cbInDrive).isChecked = Plataforma.INDRIVE in r.plataformas
        findViewById<EditText>(R.id.edAtraso).setText(config.atrasoMs.toString())
    }

    private fun lerTela() = Regras(
        ativo = sw(R.id.swAtivo).isChecked,
        minimoPorKm = num(R.id.edMinKm),
        valorMinimo = num(R.id.edValorMin),
        maxKmBusca = num(R.id.edMaxBusca),
        maxKmTotal = num(R.id.edMaxTotal),
        contarKmBusca = sw(R.id.swContarBusca).isChecked,
        bairrosPermitidos = linhas(R.id.edPermitidos),
        bairrosBloqueados = linhas(R.id.edBloqueados),
        plataformas = buildSet {
            if (cb(R.id.cbUber).isChecked) add(Plataforma.UBER)
            if (cb(R.id.cb99).isChecked) add(Plataforma.NOVENTA_E_NOVE)
            if (cb(R.id.cbInDrive).isChecked) add(Plataforma.INDRIVE)
        },
        ignorarEmCorrida = sw(R.id.swIgnorarEmCorrida).isChecked,
        aceitarNoFinalDaCorrida = sw(R.id.swAceitarNoFinal).isChecked,
        kmFinalParaAceitar = num(R.id.edKmFinal) ?: 1.0,
    )

    private fun sw(id: Int) = findViewById<CompoundButton>(id)
    private fun cb(id: Int) = findViewById<CheckBox>(id)
    private fun txt(id: Int, v: Double?) = findViewById<EditText>(id).setText(v?.toString()?.replace('.', ',') ?: "")
    private fun num(id: Int) = findViewById<EditText>(id).text.toString().trim().replace(',', '.').toDoubleOrNull()
    private fun linhas(id: Int) = findViewById<EditText>(id).text.toString()
        .split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
