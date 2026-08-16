package com.example.combinedsimulations.newtonian

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
    private val violetPaint = Paint().apply { color = Color.rgb(138, 43, 226); style = Paint.Style.FILL; isAntiAlias = true }
    private val goalPaint = Paint().apply { color = Color.rgb(135, 206, 235); style = Paint.Style.FILL; isAntiAlias = true }
    private val obstaclePaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL; isAntiAlias = true }
    private val centerOfMassPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
    private val backgroundPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 30f; isAntiAlias = true }

    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setSimulation(sim: ParticleSimulation) {
        simulation = sim
    }

    fun addObstacleAtScreenPosition(screenX: Float, screenY: Float) {
        simulation?.let { sim ->
            val simX = (screenX - offsetX) / scaleX
            val simY = (screenY - offsetY) / scaleY
            sim.addObstacle(simX, simY)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        simulation?.let { sim ->
            scaleX = w / sim.getWorldWidth()
            scaleY = scaleX
            offsetX = w / 2f
            offsetY = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val sim = simulation ?: return

        val comScreenX = sim.centerOfMassX * scaleX + offsetX
        val comScreenY = sim.centerOfMassY * scaleY + offsetY
        canvas.drawCircle(comScreenX, comScreenY, 12f, centerOfMassPaint)

        for (goal in sim.getGoals()) {
            val x = goal.position.x * scaleX + offsetX
            val y = goal.position.y * scaleY + offsetY
            canvas.drawCircle(x, y, 15f, goalPaint)
        }

        for (obstacle in sim.getObstacles()) {
            val x = obstacle.position.x * scaleX + offsetX
            val y = obstacle.position.y * scaleY + offsetY
            val radius = sim.params.obstacleSize * scaleX
            canvas.drawCircle(x, y, radius, obstaclePaint)
        }

        for (particle in sim.getParticles()) {
            val x = particle.position.x * scaleX + offsetX
            val y = particle.position.y * scaleY + offsetY
            val paint = when {
                particle.mass > 1f -> violetPaint
                particle.color == 0 -> whitePaint
                else -> yellowPaint
            }
            canvas.drawCircle(x, y, 2f, paint)
        }

        drawText(canvas, "Particles: " + sim.getParticles().size, 10f, 30f)
        drawText(canvas, "Disabled: " + sim.disabledCount, 10f, 60f)
        drawText(canvas, "Obstacles: " + sim.getObstacles().size, 10f, 90f)
        drawText(canvas, "Square: " + sim.isSquareFormation, 10f, 120f)
        drawText(canvas, "Goal: " + sim.isGoalEnabled, 10f, 150f)
        drawText(canvas, String.format("G Phase Transition: %.2f", sim.getPhaseTransitionG()), 10f, 180f)
        drawText(canvas, String.format("Abandon Goal Force: %.2f", sim.getAbandonGoalForce()), 10f, 210f)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
        canvas.drawText(text, x, y, textPaint)
    }
}
