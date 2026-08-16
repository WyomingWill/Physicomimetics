package com.example.combinedsimulations.dinos

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class DinosSimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: DinosSimulation? = null

    private val gridSize = 151
    private val pixelBuffer = IntArray(gridSize * gridSize)
    private val chemBitmap: Bitmap = Bitmap.createBitmap(gridSize, gridSize, Bitmap.Config.ARGB_8888)
    private val bitmapSrcRect = Rect(0, 0, gridSize, gridSize)
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val dstRect = RectF()

    private val dronePaint = Paint().apply { color = Color.rgb(255, 165, 0); style = Paint.Style.FILL; isAntiAlias = true }
    private val dinoPaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL; isAntiAlias = true }
    private val goalPaint = Paint().apply { color = Color.rgb(135, 206, 235); style = Paint.Style.FILL; isAntiAlias = true }
    private val trailPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 22f; isAntiAlias = true }
    private val textBgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val textBounds = Rect()

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setSimulation(sim: DinosSimulation) {
        simulation = sim
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
        canvas.drawColor(Color.BLACK)
        val sim = simulation ?: return

        updateChemicalBitmap(sim)
        canvas.drawBitmap(chemBitmap, bitmapSrcRect, dstRect, bitmapPaint)

        val gx = sim.goalX * scale + offsetX
        val gy = -sim.goalY * scale + offsetY
        canvas.drawCircle(gx, gy, 8f, goalPaint)

        for ((tx, ty) in sim.comTrail) {
            val x = tx * scale + offsetX
            val y = -ty * scale + offsetY
            canvas.drawCircle(x, y, 2f, trailPaint)
        }

        for (dino in sim.dinos) {
            val x = dino.x * scale + offsetX
            val y = -dino.y * scale + offsetY
            canvas.drawCircle(x, y, 1.5f, dinoPaint)
        }

        for (drone in sim.drones) {
            val x = drone.x * scale + offsetX
            val y = -drone.y * scale + offsetY
            canvas.drawCircle(x, y, 5f, dronePaint)
        }

        drawText(canvas, "Drones: " + sim.drones.size, 10f, 24f)
        drawText(canvas, "Dinos: " + sim.dinos.size, 10f, 48f)
        drawText(canvas, "Goal On: " + sim.goalEnabled, 10f, 72f)
        drawText(canvas, "Minimize: " + sim.minimizeChemical, 10f, 96f)
        drawText(canvas, "Done: " + sim.allDone, 10f, 120f)
        drawText(canvas, String.format("Avg Chemical: %.4f", sim.averageChemicalSeen()), 10f, 144f)
    }

    private fun updateChemicalBitmap(sim: DinosSimulation) {
        val chem = sim.getChemicalGrid()
        for (row in 0 until gridSize) {
            val destRow = gridSize - 1 - row
            val srcBase = row * gridSize
            val destBase = destRow * gridSize
            for (col in 0 until gridSize) {
                val v = (chem[srcBase + col] / 6f).coerceIn(0f, 1f)
                val g = (v * 255).toInt()
                pixelBuffer[destBase + col] = Color.rgb(0, g, 0)
            }
        }
        chemBitmap.setPixels(pixelBuffer, 0, gridSize, 0, 0, gridSize, gridSize)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawRect(x - 2, y - textBounds.height() - 2f, x + textBounds.width() + 2f, y + 2f, textBgPaint)
        canvas.drawText(text, x, y, textPaint)
    }
}
