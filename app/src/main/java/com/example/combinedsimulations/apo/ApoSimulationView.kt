package com.example.combinedsimulations.apo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ApoSimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: ApoSimulation? = null

    private val gridSize = 501
    private val pixelBuffer = IntArray(gridSize * gridSize)
    private val fitnessBitmap: Bitmap = Bitmap.createBitmap(gridSize, gridSize, Bitmap.Config.ARGB_8888)
    private val bitmapSrcRect = Rect(0, 0, gridSize, gridSize)
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val dstRect = RectF()

    private var lastRenderedVersion = -1

    private val robotPaint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL; isAntiAlias = true }
    private val optimumPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL; isAntiAlias = true }
    private val trailPaint = Paint().apply { color = Color.rgb(200, 0, 200); style = Paint.Style.FILL; isAntiAlias = true }
    private val backgroundPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 24f; isAntiAlias = true }
    private val textBgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textBounds = Rect()

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setSimulation(sim: ApoSimulation) {
        simulation = sim
    }

    fun getScale(): Float = scale

    fun screenToSim(screenX: Float, screenY: Float): Pair<Float, Float> {
        val x = (screenX - offsetX) / scale
        val y = -(screenY - offsetY) / scale
        return x to y
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        simulation?.let { sim ->
            scale = minOf(w, h) / sim.getWorldSize()
            offsetX = w / 2f
            offsetY = h / 2f
            val boundary = sim.getBoundary()
            dstRect.set(
                -boundary * scale + offsetX,
                -boundary * scale + offsetY,
                boundary * scale + offsetX,
                boundary * scale + offsetY
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val sim = simulation ?: return

        if (sim.showFitness) {
            if (sim.fitnessVersion != lastRenderedVersion) {
                updateFitnessBitmap(sim)
                lastRenderedVersion = sim.fitnessVersion
            }
            canvas.drawBitmap(fitnessBitmap, bitmapSrcRect, dstRect, bitmapPaint)
        }

        val ox = sim.optX * scale + offsetX
        val oy = -sim.optY * scale + offsetY
        val optRadius = (6f * scale).coerceAtLeast(4f)
        canvas.drawCircle(ox, oy, optRadius, optimumPaint)

        for ((tx, ty) in sim.comTrail) {
            val x = tx * scale + offsetX
            val y = -ty * scale + offsetY
            canvas.drawCircle(x, y, 2f, trailPaint)
        }

        for (robot in sim.robots) {
            val x = robot.x * scale + offsetX
            val y = -robot.y * scale + offsetY
            canvas.drawCircle(x, y, 6f, robotPaint)
        }

        drawText(canvas, "Robots: " + sim.robots.size, 10f, 26f)
        drawText(canvas, "Zoom: " + sim.zoom, 10f, 52f)
        drawText(canvas, String.format("Distance to Optimum: %.3f", sim.apomin), 10f, 78f)
    }

    private fun updateFitnessBitmap(sim: ApoSimulation) {
        val fit = sim.getFitnessGrid()
        val range = (sim.fitnessMax - sim.fitnessMin).let { if (it <= 0f) 1f else it }
        for (row in 0 until gridSize) {
            val destRow = gridSize - 1 - row
            val srcBase = row * gridSize
            val destBase = destRow * gridSize
            for (col in 0 until gridSize) {
                val v = ((fit[srcBase + col] - sim.fitnessMin) / range).coerceIn(0f, 1f)
                val yellow = (v * 255).toInt()
                pixelBuffer[destBase + col] = Color.rgb(yellow, yellow, 0)
            }
        }
        fitnessBitmap.setPixels(pixelBuffer, 0, gridSize, 0, 0, gridSize, gridSize)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawRect(x - 2, y - textBounds.height() - 2f, x + textBounds.width() + 2f, y + 2f, textBgPaint)
        canvas.drawText(text, x, y, textPaint)
    }
}
