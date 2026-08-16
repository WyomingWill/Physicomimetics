package com.example.combinedsimulations.fluxotaxis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.combinedsimulations.fluxotaxis.sim.MAP_SYM_BLOCKED
import com.example.combinedsimulations.fluxotaxis.sim.Scenario
import com.example.combinedsimulations.fluxotaxis.sim.SimulationEngine
import com.example.combinedsimulations.fluxotaxis.sim.WorldMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of the Compose Canvas drawing code (SimulationScreen.kt in the standalone
 * CPTSwarmSim app) to android.graphics.Canvas/Paint for this app's View-based pattern.
 *
 * The simulated grid is always square (dim x dim), but the pixel mapping isn't forced
 * to match: cellX and cellY are computed independently to fill the view's actual width
 * and height exactly, with no letterbox/pillarbox margin. On a landscape/wide canvas
 * this naturally stretches the grid horizontally (cellX > cellY) -- same underlying
 * simulation coordinates throughout, only the grid-cell-to-pixel mapping differs per
 * axis. Circular markers (source, agents) and vector arrows deliberately do NOT stretch
 * with the grid -- only their positions move with it -- since a stretched arrow would
 * visually misrepresent the wind's true direction, and stretched agent dots would just
 * be harder to read at a glance.
 */
class FluxotaxisSimulationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var simulation: FluxotaxisSimulation? = null

    fun setSimulation(sim: FluxotaxisSimulation) {
        simulation = sim
    }

    // recomputed in onDraw since the map dimension can change between runs (new map)
    private var cellX = 1f
    private var cellY = 1f
    private var drawnWidth = 1f
    private var drawnHeight = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    /** Cap on how much wider a grid cell can get relative to its height. Without this,
     *  a very wide landscape canvas fills cellX all the way out to width/dim, which can
     *  stretch the square grid dramatically on a wide tablet. 1.5 caps it at "50% wider
     *  than tall" -- any leftover horizontal space beyond that is letterboxed instead of
     *  stretched into. */
    private val maxHorizontalStretchRatio = 1.5f

    private val marginPaint = Paint().apply { color = Color.rgb(0x05, 0x07, 0x0A); style = Paint.Style.FILL }
    private val groundPaint = Paint().apply { color = Color.rgb(0x1B, 0x27, 0x33); style = Paint.Style.FILL }
    private val obstaclePaint = Paint().apply { color = Color.rgb(0x5A, 0x6B, 0x7A); style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { color = Color.rgb(0x3D, 0x4F, 0x5C); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val sourcePaint = Paint().apply { color = Color.rgb(0x00, 0xE5, 0xFF); style = Paint.Style.FILL; isAntiAlias = true }
    private val sourceRingPaint = Paint().apply { color = Color.rgb(0x00, 0xE5, 0xFF); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    private val agentPaint = Paint().apply { color = Color.rgb(0xFF, 0x00, 0xE5); style = Paint.Style.FILL; isAntiAlias = true }
    private val vectorPaint = Paint().apply { color = Color.rgb(0x4F, 0xA9, 0x7C); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    private val plumeCellPaint = Paint().apply { style = Paint.Style.FILL }
    private val statusPaint = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true }

    /** Port of build_colors()'s default palette + plot_scalar()'s normalization from the
     *  original CFD_VISgl.c: blue -> cyan -> green -> yellow -> red, 5-segment linear. */
    private fun rainbowColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val slope = 5f
        val r: Float; val g: Float; val b: Float
        when {
            c < 0.2f -> { r = 0f; g = 0f; b = slope * c }
            c < 0.4f -> { r = 0f; g = slope * (c - 0.2f); b = 1f }
            c < 0.6f -> { r = 0f; g = 1f; b = 1f - slope * (c - 0.4f) }
            c < 0.8f -> { r = slope * (c - 0.6f); g = 1f; b = 0f }
            else -> { r = 1f; g = 1f - slope * (c - 0.8f); b = 0f }
        }
        return Color.rgb((r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255))
    }

    /** Converts a screen touch point to a grid cell, accounting for the letterbox offset
     *  -- must use the exact same cellX/cellY/offset math as onDraw or taps land on the
     *  wrong cell. Returns null if there's no map to tap yet. */
    fun screenToGrid(screenX: Float, screenY: Float): Pair<Int, Int>? {
        val sim = simulation ?: return null
        val map = sim.displayMap ?: return null
        val dim = map.nx
        if (cellX <= 0f || cellY <= 0f) return null
        val gx = ((screenX - offsetX) / cellX).toInt().coerceIn(0, dim - 1)
        val gy = ((screenY - offsetY) / cellY).toInt().coerceIn(0, dim - 1)
        return gx to gy
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sim = simulation
        val map = sim?.displayMap

        if (sim == null || map == null) {
            canvas.drawColor(Color.rgb(0x05, 0x07, 0x0A))
            canvas.drawText(sim?.statusMessage ?: "Loading...", 24f, 48f, statusPaint)
            return
        }

        val dim = map.nx
        cellY = height.toFloat() / dim
        val naturalCellX = width.toFloat() / dim
        cellX = min(naturalCellX, maxHorizontalStretchRatio * cellY)
        drawnWidth = dim * cellX
        drawnHeight = dim * cellY
        offsetX = (width - drawnWidth) / 2f
        offsetY = (height - drawnHeight) / 2f
        // the "circular" glyph scale: markers and arrows use this instead of cellX/cellY
        // directly, so they stay undistorted even when the grid itself is stretched
        val cellUniform = min(cellX, cellY)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), marginPaint)

        canvas.save()
        canvas.translate(offsetX, offsetY)

        canvas.drawRect(0f, 0f, drawnWidth, drawnHeight, groundPaint)

        val engine = sim.engine
        if (engine != null) {
            drawPlume(canvas, engine.plume.rho, dim, map.ny, sim.logColorScale)
            if (sim.showVectors) drawVectors(canvas, engine.plume.u, engine.plume.v, dim, map.ny, sim.vectorSpacing, sim.matchedVectorScale, cellUniform)
        }

        drawObstacles(canvas, map, dim)

        if (engine != null) {
            drawSource(canvas, sim.sourceX, sim.sourceY, cellUniform)
            drawSwarm(canvas, engine, cellUniform)
        } else if (sim.sourceX >= 0) {
            drawSource(canvas, sim.sourceX, sim.sourceY, cellUniform)
        }

        canvas.drawRect(0f, 0f, drawnWidth, drawnHeight, borderPaint)
        canvas.restore()

        canvas.drawText(sim.statusMessage, 24f, height - 24f, statusPaint)
    }

    private fun drawObstacles(canvas: Canvas, map: WorldMap, dim: Int) {
        for (i in 0 until dim) {
            for (j in 0 until map.ny) {
                if (map.world[i * map.ny + j] == MAP_SYM_BLOCKED) {
                    val x = i * cellX; val y = j * cellY
                    canvas.drawRect(x, y, x + cellX, y + cellY, obstaclePaint)
                }
            }
        }
    }

    /** Port of plot_scalar() with the same threshold + optional log-scale deviations as
     *  the Compose version: cells below CHEMICAL_SENS_THRESHOLD are left as background
     *  (not painted), and remaining cells are normalized (linearly or via log(1+x)) into
     *  the brighter 78% of the rainbow (minHue=0.22) so faint plume edges stay visible. */
    private fun drawPlume(canvas: Canvas, rho: DoubleArray, nx: Int, ny: Int, logScale: Boolean) {
        val threshold = Scenario.CHEMICAL_SENS_THRESHOLD
        var maxRho = threshold
        for (v in rho) if (v > maxRho) maxRho = v

        val range = if (logScale) {
            ln(1.0 + maxRho - threshold).let { if (it == 0.0) 1.0 else it }
        } else {
            (maxRho - threshold).let { if (it == 0.0) 1.0 else it }
        }
        val minHue = 0.22f

        for (i in 0 until nx) {
            for (j in 0 until ny) {
                val value = rho[i * ny + j]
                if (value < threshold) continue
                val raw = if (logScale) ln(1.0 + value - threshold) else (value - threshold)
                val t = (raw / range).toFloat().coerceIn(0f, 1f)
                val hue = minHue + t * (1f - minHue)
                plumeCellPaint.color = rainbowColor(hue)
                val x = i * cellX; val y = j * cellY
                canvas.drawRect(x, y, x + cellX, y + cellY, plumeCellPaint)
            }
        }
    }

    /** Port of gaverage() + plot_vector(). matchedScale is the original's "1:1" checkbox:
     *  true = U/V share one scale (true proportions); false = each axis independently
     *  stretched to its own max, which exaggerates the smaller-magnitude axis.
     *  Arrow anchors move with the (possibly stretched) grid via cellX/cellY, but arrow
     *  length/shape itself uses cellUniform so the wind direction isn't visually distorted
     *  by an unrelated screen-aspect-ratio choice. */
    private fun drawVectors(canvas: Canvas, u: DoubleArray, v: DoubleArray, nx: Int, ny: Int, spacing: Int, matchedScale: Boolean, cellUniform: Float) {
        if (spacing < 1) return
        var maxU = 0.0; var maxV = 0.0
        for (value in u) { val a = abs(value); if (a > maxU) maxU = a }
        for (value in v) { val a = abs(value); if (a > maxV) maxV = a }
        if (matchedScale) {
            val shared = max(maxU, maxV)
            maxU = shared; maxV = shared
        }
        if (!maxU.isFinite() || maxU <= 0.0) maxU = 1.0
        if (!maxV.isFinite() || maxV <= 0.0) maxV = 1.0

        val arrowLen = 0.97f * cellUniform * spacing
        val thetaIncr = (Math.PI / 16).toFloat()
        val arrowheadReduction = 0.7f

        var i = 0
        while (i + spacing <= nx) {
            var j = 0
            while (j + spacing <= ny) {
                val uAvg = blockAverage(u, ny, i, j, spacing)
                val vAvg = blockAverage(v, ny, i, j, spacing)

                val ax = (i + spacing / 2f) * cellX
                val ay = (j + spacing / 2f) * cellY
                val bx = ax + arrowLen * (uAvg / maxU).toFloat()
                val by = ay + arrowLen * (vAvg / maxV).toFloat()

                if (!bx.isFinite() || !by.isFinite()) { j += spacing; continue }

                canvas.drawLine(ax, ay, bx, by, vectorPaint)

                val theta = atan2(by - ay, bx - ax)
                val baseLen = arrowheadReduction * hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
                val tipLx = ax + baseLen * cos(theta + thetaIncr); val tipLy = ay + baseLen * sin(theta + thetaIncr)
                val tipRx = ax + baseLen * cos(theta - thetaIncr); val tipRy = ay + baseLen * sin(theta - thetaIncr)
                canvas.drawLine(bx, by, tipLx, tipLy, vectorPaint)
                canvas.drawLine(bx, by, tipRx, tipRy, vectorPaint)

                j += spacing
            }
            i += spacing
        }
    }

    private fun blockAverage(data: DoubleArray, ny: Int, row: Int, col: Int, size: Int): Double {
        var sum = 0.0
        for (r in row until row + size) for (c in col until col + size) sum += data[r * ny + c]
        return sum / (size * size)
    }

    private fun drawSource(canvas: Canvas, sourceX: Int, sourceY: Int, cellUniform: Float) {
        if (sourceX < 0) return
        val cx = (sourceX + 0.5f) * cellX
        val cy = (sourceY + 0.5f) * cellY
        canvas.drawCircle(cx, cy, max(4f, cellUniform), sourcePaint)
        // Display-only scale-down of the SENS_RADIUS "goal" ring, same as the Compose port --
        // purely visual, doesn't touch the real Scenario.SENS_RADIUS used for gameplay.
        val goalRingVisualScale = 0.35f
        canvas.drawCircle(cx, cy, Scenario.SENS_RADIUS.toFloat() * cellUniform * goalRingVisualScale, sourceRingPaint)
    }

    private fun drawSwarm(canvas: Canvas, engine: SimulationEngine, cellUniform: Float) {
        for (agent in engine.swarm.agents) {
            val cx = (agent.x.toFloat() + 0.5f) * cellX
            val cy = (agent.y.toFloat() + 0.5f) * cellY
            canvas.drawCircle(cx, cy, max(3f, cellUniform * 0.5f), agentPaint)
        }
    }
}
