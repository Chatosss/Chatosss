package br.com.autocorrida

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Bolinha flutuante sobre os apps de corrida, desenhada pelo serviço de acessibilidade
 * (TYPE_ACCESSIBILITY_OVERLAY não precisa da permissão "sobrepor a outros apps").
 *
 *  - toque: liga/desliga o aceite automático
 *  - arrastar: move (gruda na borda mais próxima)
 *  - segurar: abre o app
 */
class BolinhaFlutuante(
    private val service: AccessibilityService,
    private val config: Configuracoes,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val densidade = service.resources.displayMetrics.density
    private val tamanho = (56 * densidade).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val toqueMinimo = ViewConfiguration.get(service).scaledTouchSlop

    private var view: TextView? = null
    private val fundo = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private val params = WindowManager.LayoutParams(
        tamanho, tamanho,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    val visivel: Boolean get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun mostrar() {
        if (view != null) return
        val (x, y) = config.posicaoBolinha
        val tela = service.resources.displayMetrics
        params.x = if (x >= 0) x else tela.widthPixels - tamanho - (8 * densidade).toInt()
        params.y = if (y >= 0) y else tela.heightPixels / 3

        val v = TextView(service).apply {
            background = fundo
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            elevation = 6 * densidade
            contentDescription = "Corrida Auto: tocar para ligar ou desligar o aceite automático"
        }
        v.setOnTouchListener(TratadorDeToque())
        view = v
        atualizar()
        runCatching { wm.addView(v, params) }.onFailure { view = null }
    }

    fun esconder() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    /** Redesenha conforme o estado: verde = ligado, cinza = desligado, anel azul = em corrida. */
    fun atualizar() {
        val v = view ?: return
        val ligado = config.ativo
        fundo.setColor(if (ligado) Color.parseColor("#2E7D32") else Color.parseColor("#616161"))
        val emCorrida = Historico.estado.emCorrida
        fundo.setStroke(
            (if (emCorrida) 4 else 2) * densidade.toInt().coerceAtLeast(1),
            if (emCorrida) Color.parseColor("#42A5F5") else Color.WHITE,
        )
        v.text = if (ligado) "ON" else "OFF"
        v.alpha = if (ligado) 1f else 0.85f
    }

    private fun alternar() {
        val novo = !config.ativo
        config.ativo = novo // o serviço observa as prefs e chama atualizar()
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        Toast.makeText(
            service,
            if (novo) "Aceite automático LIGADO" else "Aceite automático DESLIGADO",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun abrirApp() {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private inner class TratadorDeToque : android.view.View.OnTouchListener {
        private var inicioX = 0f
        private var inicioY = 0f
        private var paramX = 0
        private var paramY = 0
        private var arrastando = false
        private var segurou = false
        private val segurar = Runnable { segurou = true; abrirApp() }

        override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = e.rawX; inicioY = e.rawY
                    paramX = params.x; paramY = params.y
                    arrastando = false; segurou = false
                    handler.postDelayed(segurar, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - inicioX
                    val dy = e.rawY - inicioY
                    if (!arrastando && (abs(dx) > toqueMinimo || abs(dy) > toqueMinimo)) {
                        arrastando = true
                        handler.removeCallbacks(segurar)
                    }
                    if (arrastando) {
                        params.x = (paramX + dx).toInt()
                        params.y = (paramY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(segurar)
                    when {
                        arrastando -> grudarNaBorda(v)
                        !segurou -> alternar()
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(segurar)
            }
            return true
        }

        private fun grudarNaBorda(v: android.view.View) {
            val tela = service.resources.displayMetrics
            val margem = (8 * densidade).toInt()
            params.x = if (params.x + tamanho / 2 < tela.widthPixels / 2) margem
            else tela.widthPixels - tamanho - margem
            params.y = params.y.coerceIn(0, (tela.heightPixels - tamanho).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(v, params) }
            config.posicaoBolinha = params.x to params.y
        }
    }
}
