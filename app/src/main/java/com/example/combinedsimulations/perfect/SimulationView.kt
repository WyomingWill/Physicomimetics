package com.example.combinedsimulations.perfect

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: ParticleSimulation? = null

    private val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    private val yellowPaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL; isAntiAlias = true }
    private val trailPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
    private val backgroundPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 30f; isAntiAlias = true }

    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setSimulation(sim: ParticleSimulation) {
        simulation = sim
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        simulation?.let { sim ->
            scaleX = w / sim.getWorldSize()
            scaleY = scaleX
            offsetX = w / 2f
            offsetY = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val sim = simulation ?: return

        for ((tx, ty) in sim.comTrail) {
            val x = tx * scaleX + offsetX
            val y = -ty * scaleY + offsetY
            canvas.drawCircle(x, y, (4f * scaleX).coerceAtLeast(2f), trailPaint)
        }

        for (particle in sim.getParticles()) {
            val x = particle.x * scaleX + offsetX
            val y = -particle.y * scaleY + offsetY
            val paint = if (particle.color == 0) whitePaint else yellowPaint
            canvas.drawCircle(x, y, 2f, paint)
        }

        drawText(canvas, "Particles: " + sim.getParticles().size, 10f, 30f)
        drawText(canvas, "Square: " + sim.isSquareFormation, 10f, 60f)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
        canvas.drawText(text, x, y, textPaint)
    }
}
