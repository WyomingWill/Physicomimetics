package com.example.combinedsimulations.coverage

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CoverageSimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: UniformCoverageSimulation? = null

    private val agentPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    private val bluePaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
    private val greenPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val redPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val yellowPaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 30f; isAntiAlias = true }
    private val gridLinePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setSimulation(sim: UniformCoverageSimulation) {
        simulation = sim
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        simulation?.let { sim ->
            val worldSize = sim.getWorldSize()
            scale = minOf(w, h) / worldSize
            offsetX = w / 2f
            offsetY = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val sim = simulation ?: return

        val boundary = sim.getBoundary()
        val b1 = sim.getB1()
        val b2 = sim.getB2()

        drawCell(canvas, -boundary, b1, -boundary, b1, bluePaint)
        drawCell(canvas, b1, b2, -boundary, b1, greenPaint)
        drawCell(canvas, b2, boundary, -boundary, b1, bluePaint)

        drawCell(canvas, -boundary, b1, b1, b2, greenPaint)
        drawCell(canvas, b1, b2, b1, b2, redPaint)
        drawCell(canvas, b2, boundary, b1, b2, greenPaint)

        drawCell(canvas, -boundary, b1, b2, boundary, bluePaint)
        drawCell(canvas, b1, b2, b2, boundary, greenPaint)
        drawCell(canvas, b2, boundary, b2, boundary, bluePaint)

        drawGridLines(canvas, boundary, b1, b2)
        drawBoundary(canvas, boundary)

        for (agent in sim.getAgents()) {
            val screenX = agent.x * scale + offsetX
            val screenY = -agent.y * scale + offsetY
            canvas.drawCircle(screenX, screenY, 6f, agentPaint)
        }

        drawStats(canvas, sim)
    }

    private fun drawCell(canvas: Canvas, x1: Float, x2: Float, y1: Float, y2: Float, paint: Paint) {
        val screenX1 = x1 * scale + offsetX
        val screenX2 = x2 * scale + offsetX
        val screenY1 = -y1 * scale + offsetY
        val screenY2 = -y2 * scale + offsetY

        canvas.drawRect(screenX1, screenY2, screenX2, screenY1, paint)
    }

    private fun drawGridLines(canvas: Canvas, boundary: Float, b1: Float, b2: Float) {
        drawLine(canvas, b1, -boundary, b1, boundary, gridLinePaint)
        drawLine(canvas, b2, -boundary, b2, boundary, gridLinePaint)
        drawLine(canvas, -boundary, b1, boundary, b1, gridLinePaint)
        drawLine(canvas, -boundary, b2, boundary, b2, gridLinePaint)
    }

    private fun drawBoundary(canvas: Canvas, boundary: Float) {
        val x1 = -boundary * scale + offsetX
        val x2 = boundary * scale + offsetX
        val y1 = -boundary * scale + offsetY
        val y2 = boundary * scale + offsetY

        canvas.drawRect(x1, y2, x2, y1, yellowPaint)
    }

    private fun drawLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val screenX1 = x1 * scale + offsetX
        val screenX2 = x2 * scale + offsetX
        val screenY1 = -y1 * scale + offsetY
        val screenY2 = -y2 * scale + offsetY

        canvas.drawLine(screenX1, screenY1, screenX2, screenY2, paint)
    }

    private fun drawStats(canvas: Canvas, sim: UniformCoverageSimulation) {
        drawText(canvas, "Agents: " + sim.getAgents().size, 10f, 30f)
        drawText(canvas, "Ticks: " + sim.ticks, 10f, 60f)
        drawText(canvas, String.format("Deviation: %.4f", sim.getUniformityDeviation()), 10f, 90f)
        drawText(canvas, String.format("Theoretical MFP: %.1f", sim.getTheoreticalMeanFreePath()), 10f, 120f)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
        val bgPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawRect(x - 2, y - bounds.height() - 2f, x + bounds.width() + 2f, y + 2f, bgPaint)
        canvas.drawText(text, x, y, textPaint)
    }
}
