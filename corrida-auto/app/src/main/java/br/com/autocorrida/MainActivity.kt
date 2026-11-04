package br.com.autocorrida

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import br.com.autocorrida.core.Fase
import br.com.autocorrida.core.Plataforma
import br.com.autocorrida.core.Regras

class MainActivity : AppCompatActivity() {

    private lateinit var config: Configuracoes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        ajustarBordas()
        config = Configuracoes(this)
        preencher(config.carregar())
        AtualizacaoRemota.carregarCache(this)

        // Estes dois valem na hora (a bolinha acompanha), sem precisar tocar em Salvar
        sw(R.id.swAtivo).setOnCheckedChangeListener { _, v -> if (config.ativo != v) config.ativo = v }
        sw(R.id.swBolinha).setOnCheckedChangeListener { _, v -> if (config.mostrarBolinha != v) config.mostrarBolinha = v }

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
            config.mostrarBolinha = sw(R.id.swBolinha).isChecked
            Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Historico.aoMudar = { runOnUiThread { atualizarStatus() } }
        // A bolinha pode ter ligado/desligado o aceite enquanto o app estava fechado
        sw(R.id.swAtivo).isChecked = config.ativo
        atualizarStatus()
    }

    override fun onPause() {
        super.onPause()
        Historico.aoMudar = null
    }

    /**
     * No Android 15+ o app desenha de ponta a ponta. Reserva espaço para a barra de status
     * (em cima), a barra de navegação (embaixo) e o teclado, para nada ficar por baixo delas.
     */
    private fun ajustarBordas() {
        val toolbar = findViewById<View>(R.id.toolbar)
        val rolagem = findViewById<View>(R.id.rolagem)
        val alturaToolbar = toolbar.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz)) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            toolbar.updatePadding(top = barras.top, left = barras.left, right = barras.right)
            toolbar.layoutParams = toolbar.layoutParams.apply { height = alturaToolbar + barras.top }
            rolagem.updatePadding(
                left = barras.left,
                right = barras.right,
                bottom = maxOf(barras.bottom, teclado.bottom),
            )
            WindowInsetsCompat.CONSUMED
        }
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
        sw(R.id.swBolinha).isChecked = config.mostrarBolinha
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
        sw(R.id.swRecusarPorKm).isChecked = r.recusarAbaixoPorKm
        sw(R.id.swRecusarOutros).isChecked = r.recusarOutrosFiltros
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
        recusarAbaixoPorKm = sw(R.id.swRecusarPorKm).isChecked,
        recusarOutrosFiltros = sw(R.id.swRecusarOutros).isChecked,
    )

    private fun sw(id: Int) = findViewById<CompoundButton>(id)
    private fun cb(id: Int) = findViewById<CheckBox>(id)
    private fun txt(id: Int, v: Double?) = findViewById<EditText>(id).setText(v?.toString()?.replace('.', ',') ?: "")
    private fun num(id: Int) = findViewById<EditText>(id).text.toString().trim().replace(',', '.').toDoubleOrNull()
    private fun linhas(id: Int) = findViewById<EditText>(id).text.toString()
        .split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
