package com.example.cviko2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Locale
import kotlin.math.max

/**
 * Custom View pre vizualizáciu a animáciu šikmého vrhu.
 *
 * ODPOVEDE NA OTÁZKY:
 *
 * 1. LINEÁRNY INTERPOLÁTOR (LinearInterpolator):
 *    - Určuje priebeh zmeny hodnôt v čase: f(t) = t.
 *    - Pri lineárnom interpolátore beží animácia konštantnou rýchlosťou (bez zrýchlenia na začiatku
 *      alebo spomalenia na konci, ako to robí napr. AccelerateDecelerateInterpolator).
 *
 * 2. PIXEL PER METER (Prepočet metrov na pixely):
 *    - Fyzikálne výpočty pracujú v metroch (napr. x = 200m, y = 50m), ale obrazovka Androidu v pixeloch.
 *    - Prepočet (mierka) sa počíta dynamicky v metóde [recomputeScalesAndPaths]:
 *        scaleX = availableWidthInPixels / maxDistanceInMeters  [px/m]
 *        scaleY = availableHeightInPixels / maxHeightInMeters   [px/m]
 *    - Pozícia na obrazovke v pixeloch:
 *        pixelX = originX + (meterX * scaleX)
 *        pixelY = originY - (meterY * scaleY)  // V Androide rastie Y smerom dole, preto odpočítavame
 *
 * 3. TAKT A PREKRESĽOVANIE (FPS / Frames Per Second):
 *    - Animáciu riadi [ValueAnimator], ktorý je synchronizovaný so systémovým taktom obrazovky (V-Sync / Choreographer, zvyčajne 60Hz alebo 120Hz).
 *    - Pri každom takte sa zavolá [addUpdateListener], kde:
 *        1. Získame aktuálny index bodu: currentPointIndex = animation.animatedValue
 *        2. Zavoláme [invalidate()] -> tým oznámime Androidu, že View je neplatný a musí sa znova prekresliť metódou [onDraw].
 *
 * 4. KDE ZMENIŤ RÝCHLOSŤ ANIMÁCIE:
 *    - V metóde [startAnimation] nastavujeme vlastnosť:
 *        animator.duration = flightDuration  // Čas trvania animácie v milisekundách (ms).
 *    - Ak chcete animáciu ZRÝCHLIŤ: zmenšite hodnotu duration (napr. duration = 1500L alebo duration / 2).
 *    - Ak chcete animáciu SPOMALIŤ: zväčšite hodnotu duration (napr. duration = 6000L).
 */
class TrajectoryAnimationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Zoznam bodov trajektórie vypočítaných serverom
    private var trajectoryPoints: List<TrajectoryPointParcel> = emptyList()
    private var currentPointIndex = 0
    private var animator: ValueAnimator? = null

    // Callback pre aktualizáciu externého TextView vo chvíli zmeny bodu
    var onPointUpdateListener: ((point: TrajectoryPointParcel) -> Unit)? = null

    // Fyzikálne maximá pre škálovanie
    private var maxX = 100.0
    private var maxY = 50.0
    private var totalTime = 0.0

    // Okraje plátna v pixeloch (pre osi, texty a HUD)
    private val marginLeft = 110f
    private val marginRight = 60f
    private val marginTop = 120f
    private val marginBottom = 120f

    // Počiatok súradníc (0, 0) v pixeloch
    private var originX = marginLeft
    private var originY = 0f

    // Koeficienty prepočtu: koľko pixelov pripadá na 1 meter (Pixels per Meter)
    private var scaleX = 1f
    private var scaleY = 1f

    // Krivky pre vykreslenie
    private val fullPath = Path()
    private val traveledPath = Path()

    // --- Farby a štýly (Paints) ---
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val fullPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBDEFB")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val traveledPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }

    private val ballGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCDD2")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        textSize = 28f
    }

    private val axisTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        textSize = 32f
        isFakeBoldText = true
    }

    private val hudCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }

    private val hudCardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A237E")
        textSize = 32f
        isFakeBoldText = true
    }

    /**
     * Nastaví body trajektórie a spustí novú animáciu
     */
    fun setTrajectory(points: List<TrajectoryPointParcel>) {
        this.trajectoryPoints = points
        this.currentPointIndex = 0

        if (points.isNotEmpty()) {
            maxX = max(points.maxOf { it.x }, 1.0)
            maxY = max(points.maxOf { it.y }, 1.0)
            totalTime = points.last().time
        }

        recomputeScalesAndPaths()
        stopAnimation()
        if (points.isNotEmpty()) {
            startAnimation()
        }
        invalidate()
    }

    /**
     * Prepočet škálovania (Pixels per meter) podľa aktuálnej šírky a výšky obrazovky
     */
    private fun recomputeScalesAndPaths() {
        if (width == 0 || height == 0 || trajectoryPoints.isEmpty()) return

        originX = marginLeft
        originY = height - marginBottom

        val availableW = width - marginLeft - marginRight
        val availableH = height - marginTop - marginBottom

        // Výpočet koeficientov px/m (scaleX, scaleY)
        scaleX = (availableW / (maxX * 1.05)).toFloat()
        scaleY = (availableH / (maxY * 1.15)).toFloat()

        // Pripravenie celej trajektórie (vodiaca čiara)
        fullPath.reset()
        val first = trajectoryPoints.first()
        fullPath.moveTo(originX + (first.x * scaleX).toFloat(), originY - (first.y * scaleY).toFloat())
        for (p in trajectoryPoints) {
            fullPath.lineTo(originX + (p.x * scaleX).toFloat(), originY - (p.y * scaleY).toFloat())
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeScalesAndPaths()
    }

    /**
     * Vykreslenie plátna (Canvas) pri každom volaní invalidate()
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val rightX = width - marginRight
        val topY = marginTop

        // 1. Vykreslenie súradnicových osí, značiek a mriežky
        drawGridAndAxes(canvas, rightX, topY)

        // 2. Vykreslenie celej trajektórie (čiarkovaná vodiaca krivka)
        canvas.drawPath(fullPath, fullPathPaint)

        // 3. Vykreslenie prejdenej dráhy a letiaceho projektilu
        if (trajectoryPoints.isNotEmpty() && currentPointIndex < trajectoryPoints.size) {
            traveledPath.reset()
            val first = trajectoryPoints.first()
            traveledPath.moveTo(originX + (first.x * scaleX).toFloat(), originY - (first.y * scaleY).toFloat())
            for (i in 0..currentPointIndex) {
                val p = trajectoryPoints[i]
                traveledPath.lineTo(originX + (p.x * scaleX).toFloat(), originY - (p.y * scaleY).toFloat())
            }
            canvas.drawPath(traveledPath, traveledPathPaint)

            // Vykreslenie projektilu (guľa s jemnou žiarou)
            val cur = trajectoryPoints[currentPointIndex]
            val ballX = originX + (cur.x * scaleX).toFloat()
            val ballY = originY - (cur.y * scaleY).toFloat()

            canvas.drawCircle(ballX, ballY, 22f, ballGlowPaint)
            canvas.drawCircle(ballX, ballY, 14f, ballPaint)
        }

        // 4. Vykreslenie horného HUD panelu so živými štatistikami
        drawHudOverlay(canvas)
    }

    /**
     * Vykreslenie osí X a Y s jednotkami v metroch
     */
    private fun drawGridAndAxes(canvas: Canvas, rightX: Float, topY: Float) {
        // Os X
        canvas.drawLine(originX, originY, rightX + 20f, originY, axisPaint)
        // Os Y
        canvas.drawLine(originX, originY, originX, topY - 20f, axisPaint)

        // Šípky na koncoch osí
        canvas.drawLine(rightX + 20f, originY, rightX + 5f, originY - 10f, axisPaint)
        canvas.drawLine(rightX + 20f, originY, rightX + 5f, originY + 10f, axisPaint)
        canvas.drawLine(originX, topY - 20f, originX - 10f, topY - 5f, axisPaint)
        canvas.drawLine(originX, topY - 20f, originX + 10f, topY - 5f, axisPaint)

        // Názvy osí
        axisTitlePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Vzdialenosť x [m]", rightX, originY + 75f, axisTitlePaint)

        axisTitlePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Výška y [m]", originX + 15f, topY - 10f, axisTitlePaint)

        // Značky a mriežka na osi X
        val xSteps = 6
        textPaint.textAlign = Paint.Align.CENTER
        for (i in 0..xSteps) {
            val valX = (maxX * i / xSteps)
            val px = originX + (valX * scaleX).toFloat()
            canvas.drawLine(px, originY, px, topY, gridPaint)
            canvas.drawLine(px, originY, px, originY + 10f, axisPaint)
            canvas.drawText(String.format(Locale.US, "%.0fm", valX), px, originY + 40f, textPaint)
        }

        // Značky a mriežka na osi Y
        val ySteps = 5
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..ySteps) {
            val valY = (maxY * i / ySteps)
            val py = originY - (valY * scaleY).toFloat()
            canvas.drawLine(originX, py, rightX, py, gridPaint)
            canvas.drawLine(originX - 10f, py, originX, py, axisPaint)
            canvas.drawText(String.format(Locale.US, "%.0fm", valY), originX - 18f, py + 10f, textPaint)
        }
    }

    /**
     * Vykreslenie informačnej karty (HUD) v hornej časti plátna
     */
    private fun drawHudOverlay(canvas: Canvas) {
        val currentPoint = if (trajectoryPoints.isNotEmpty() && currentPointIndex < trajectoryPoints.size) {
            trajectoryPoints[currentPointIndex]
        } else if (trajectoryPoints.isNotEmpty()) {
            trajectoryPoints.last()
        } else {
            TrajectoryPointParcel(0.0, 0.0, 0.0)
        }

        val cardLeft = marginLeft
        val cardTop = 15f
        val cardRight = width - marginRight
        val cardBottom = marginTop - 20f

        val rect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(rect, 16f, 16f, hudCardPaint)
        canvas.drawRoundRect(rect, 16f, 16f, hudCardBorderPaint)

        hudTextPaint.textAlign = Paint.Align.LEFT
        val line1 = String.format(Locale.US, "Čas: %.2fs | x: %.1fm | y: %.1fm", currentPoint.time, currentPoint.x, currentPoint.y)
        val line2 = String.format(Locale.US, "Max Dolet: %.1fm | Max Výška: %.1fm", maxX, maxY)

        canvas.drawText(line1, cardLeft + 20f, cardTop + 35f, hudTextPaint)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(line2, cardLeft + 20f, cardTop + 70f, textPaint)
    }

    /**
     * Spustenie plynulej animácie pomocou ValueAnimator
     */
    private fun startAnimation() {
        if (trajectoryPoints.isEmpty() || trajectoryPoints.size <= 1) return

        // TU SA MENÍ RÝCHLOSŤ ANIMÁCIE:
        // duration určuje celkový čas prehrávania v milisekundách
        val flightDuration = (totalTime * 1000).toLong()
        val duration = if (flightDuration in 1500..8000) flightDuration else 3000L

        animator = ValueAnimator.ofInt(0, trajectoryPoints.size - 1).apply {
            this.duration = duration

            // Lineárny interpolátor zabezpečuje rovnomerný pohyb v čase
            interpolator = LinearInterpolator()

            // Taktovanie animácie: pri každom snímku (frame) sa aktualizuje index a zavolá invalidate()
            addUpdateListener { animation ->
                currentPointIndex = animation.animatedValue as Int

                // Notifikácia externého TextView
                if (currentPointIndex < trajectoryPoints.size) {
                    onPointUpdateListener?.invoke(trajectoryPoints[currentPointIndex])
                }

                // invalidate() vynúti opätovné zavolanie onDraw() na novom snímku obrazovky
                invalidate()
            }
        }
        animator?.start()
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
