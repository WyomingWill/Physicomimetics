package com.example.combinedsimulations.kinetic

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class VelocityHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: KineticTheorySimulation? = null

    private val barPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val axisPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val textPaint = Paint().apply { color = Color.BLACK; textSize = 22f; isAntiAlias = true }

    fun setSimulation(sim: KineticTheorySimulation) {
        simulation = sim
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val sim = simulation ?: return
        if (sim.getSampleCount() == 0) {
            canvas.drawText("Run simulation to see histogram", 20f, height / 2f, textPaint)
            return
        }

        val values = sim.getVelocityHistogram()
        val range = maxOf(sim.wallVelocity, 0.001f) + 0.001f
        val midY = height / 2f
        val barWidth = width / 13f

        canvas.drawLine(0f, midY, width.toFloat(), midY, axisPaint)

        for (i in 0..12) {
            val v = values[i]
            val barHeight = (v / range) * (height / 2f)
            val x = i * barWidth
            val top = if (barHeight >= 0) midY - barHeight else midY
            val bottom = if (barHeight >= 0) midY else midY - barHeight
            canvas.drawRect(x + 2, top, x + barWidth - 2, bottom, barPaint)
        }

        canvas.drawText("Distribution of Velocities", 10f, 22f, textPaint)
    }
}
