package com.example.combinedsimulations.kinetic

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2

class KineticSimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: KineticTheorySimulation? = null

    private val chevronPath = Path().apply {
        val tipLength = 3f
        val halfWidth = 1.5f
        moveTo(-tipLength * 0.5f, halfWidth)
        lineTo(tipLength, 0f)
        lineTo(-tipLength * 0.5f, -halfWidth)
    }
    private val chevronPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val redPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val yellowPaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val boundaryPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 24f; isAntiAlias = true }

    private val textBgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textBounds = Rect()

    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setSimulation(sim: KineticTheorySimulation) {
        simulation = sim
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        simulation?.let { sim ->
            val worldSize = sim.getWorldSize()
            scaleX = w / worldSize
            scaleY = h / worldSize
            offsetX = w / 2f
            offsetY = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val sim = simulation ?: return
        val boundary = sim.getBoundary()
        val wallZone = sim.getWallZoneWidth()

        val leftPx = 0f
        val rightPx = width.toFloat()
        val topPx = -boundary * scaleY + offsetY
        val bottomPx = boundary * scaleY + offsetY

        val redBottomPx = -(boundary - wallZone) * scaleY + offsetY
        canvas.drawRect(leftPx, topPx, rightPx, redBottomPx, redPaint)

        val yellowTopPx = (boundary - wallZone) * scaleY + offsetY
        canvas.drawRect(leftPx, yellowTopPx, rightPx, bottomPx, yellowPaint)

        canvas.drawRect(leftPx, topPx, rightPx, bottomPx, boundaryPaint)

        for (agent in sim.getAgents()) {
            val x = agent.y * scaleX + offsetX
            val y = -agent.x * scaleY + offsetY
            val angleDeg = Math.toDegrees(atan2(-agent.vx, agent.vy).toDouble()).toFloat()

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angleDeg)
            canvas.drawPath(chevronPath, chevronPaint)
            canvas.restore()
        }

        drawText(canvas, "Particles: " + sim.getAgents().size, 10f, 26f)
        drawText(canvas, String.format("Slope: %.5f", sim.slope), 10f, 52f)
        drawText(canvas, String.format("Correlation: %.5f", sim.correlation), 10f, 78f)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawRect(x - 2, y - textBounds.height() - 2f, x + textBounds.width() + 2f, y + 2f, textBgPaint)
        canvas.drawText(text, x, y, textPaint)
    }
}
