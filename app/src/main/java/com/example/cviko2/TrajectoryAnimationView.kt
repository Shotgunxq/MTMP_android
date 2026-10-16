package com.example.cviko2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class TrajectoryAnimationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var trajectoryPoints: List<TrajectoryPointParcel> = emptyList()
    private var currentPointIndex = 0
    private var animator: ValueAnimator? = null

    private val ballPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val groundPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 5f
    }

    private val pathPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val ballRadius = 15f
    private val pixelsPerMeter = 20f
    private var originX = 50f
    private var originY = 0f

    private val trajectoryPath = Path()

    fun setTrajectory(points: List<TrajectoryPointParcel>) {
        this.trajectoryPoints = points
        this.currentPointIndex = 0
        prepareTrajectoryPath()
        stopAnimation()
        if (points.isNotEmpty()) {
            startAnimation()
        }
        invalidate()
    }

    private fun prepareTrajectoryPath() {
        trajectoryPath.reset()
        if (trajectoryPoints.isNotEmpty()) {
            val firstPoint = trajectoryPoints.first()
            trajectoryPath.moveTo(
                originX + (firstPoint.x * pixelsPerMeter).toFloat(),
                originY - (firstPoint.y * pixelsPerMeter).toFloat()
            )
            trajectoryPoints.forEach { point ->
                trajectoryPath.lineTo(
                    originX + (point.x * pixelsPerMeter).toFloat(),
                    originY - (point.y * pixelsPerMeter).toFloat()
                )
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        originY = h - 100f
        prepareTrajectoryPath()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawLine(0f, originY, width.toFloat(), originY, groundPaint)
        canvas.drawPath(trajectoryPath, pathPaint)

        if (trajectoryPoints.isNotEmpty() && currentPointIndex < trajectoryPoints.size) {
            val currentPoint = trajectoryPoints[currentPointIndex]
            val ballCenterX = originX + (currentPoint.x * pixelsPerMeter).toFloat()
            val ballCenterY = originY - (currentPoint.y * pixelsPerMeter).toFloat()
            canvas.drawCircle(ballCenterX, ballCenterY, ballRadius, ballPaint)
        }
    }

    private fun startAnimation() {
        if (trajectoryPoints.isEmpty() || trajectoryPoints.size <= 1) return

        val totalTime = trajectoryPoints.last().time.toFloat()
        val animationDuration = (totalTime * 1000).toLong()

        animator = ValueAnimator.ofInt(0, trajectoryPoints.size - 1).apply {
            duration = if (animationDuration > 0) animationDuration else 2000L
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                currentPointIndex = animation.animatedValue as Int
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
