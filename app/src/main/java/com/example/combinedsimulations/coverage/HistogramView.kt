package com.example.combinedsimulations.coverage

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class HistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: UniformCoverageSimulation? = null

    private val bluePaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
    private val greenPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val redPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val backgroundPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 24f; isAntiAlias = true }
    private val idealLinePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    fun setSimulation(sim: UniformCoverageSimulation) {
        simulation = sim
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val sim = simulation ?: return

        val cellCounts = sim.getCellCounts()
        val ticks = sim.ticks
        val numAgents = sim.getAgents().size

        if (ticks == 0L || numAgents == 0) {
            canvas.drawText("Run simulation to see histogram", 20f, height / 2f, textPaint)
            return
        }

        val totalVisits = (ticks * numAgents).toDouble()
        val idealProportion = 1.0 / 9.0

        val barWidth = width / 9f
        val maxHeight = height - 0f
        val baseY = height - 30f

        val idealY = baseY - (5 * idealProportion * maxHeight).toFloat()
        canvas.drawLine(0f, idealY, width.toFloat(), idealY, idealLinePaint)

        for (i in 0..8) {
            val proportion = cellCounts[i] / totalVisits
            val barHeight = (5 * proportion * maxHeight).toFloat()

            val x = i * barWidth
            val y = baseY - barHeight

            val paint = when (sim.getCellColor(i)) {
                0 -> bluePaint
                1 -> greenPaint
                else -> redPaint
            }

            canvas.drawRect(x + 2, y, x + barWidth - 2, baseY, paint)
            canvas.drawText("" + i, x + barWidth / 2 - 10f, baseY + 25f, textPaint)
        }

        canvas.drawText("Distribution of Coverage", 10f, 25f, textPaint)

        textPaint.textSize = 24f
        canvas.drawText(String.format("Ideal: %.4f", idealProportion), width - 150f, idealY - 5f, textPaint)
        textPaint.textSize = 24f
    }
}
