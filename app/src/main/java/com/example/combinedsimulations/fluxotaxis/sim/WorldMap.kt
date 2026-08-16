package com.example.combinedsimulations.fluxotaxis.sim

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val MAP_SYM_BLOCKED: Byte = 'b'.code.toByte()
const val MAP_SYM_OPEN: Byte = ' '.code.toByte()

/**
 * Port of MapUtils.h's map_t and the query helpers in MapUtils.c.
 * The world is stored as a flat ByteArray of size nx*ny, row-major (index = i*ny + j),
 * exactly like the original C code.
 */
class WorldMap(val nx: Int, val ny: Int) {
    val nxy: Int = nx * ny
    val world: ByteArray = ByteArray(nxy) { MAP_SYM_OPEN }

    fun isWorldPoint(x: Double, y: Double): Boolean {
        val ix = x.roundToInt()
        val iy = y.roundToInt()
        return ix in 0 until nx && iy in 0 until ny
    }

    fun isWorldRegionSquare(x: Double, y: Double, rad: Double): Boolean =
        floor(x - rad) >= 0 && ceil(x + rad) < nx && floor(y - rad) >= 0 && ceil(y + rad) < ny

    fun isBlockedPoint(x: Double, y: Double): Boolean =
        isWorldPoint(x, y) && world[x.roundToInt() * ny + y.roundToInt()] == MAP_SYM_BLOCKED

    fun isBlockedRegionRectangle(x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val xMin = floor(min(x1, x2)); val xMax = ceil(max(x1, x2))
        val yMin = floor(min(y1, y2)); val yMax = ceil(max(y1, y2))
        var i = xMin
        while (i <= xMax) {
            var j = yMin
            while (j <= yMax) {
                if (isBlockedPoint(i, j)) return true
                j += 1.0
            }
            i += 1.0
        }
        return false
    }

    fun isBlockedRegionSquare(x: Double, y: Double, rad: Double): Boolean {
        val xMin = floor(x - rad); val xMax = ceil(x + rad)
        val yMin = floor(y - rad); val yMax = ceil(y + rad)
        var i = xMin
        while (i <= xMax) {
            var j = yMin
            while (j <= yMax) {
                if (isBlockedPoint(i, j)) return true
                j += 1.0
            }
            i += 1.0
        }
        return false
    }
}

/**
 * Scatters `numBlocks` rectangular obstacles onto a `dimension` x `dimension` square
 * map. Loosely based on MapGen.c's random placement, but with different placement
 * rules: buildings are free to overlap (they just merge into one bigger obstacle
 * mass, which is fine for navigation), but any two buildings that *don't* overlap
 * must leave a gap wide enough for an agent to actually fit through -- otherwise you
 * get those narrow, impassable slivers between two almost-touching rectangles that
 * just trap the swarm.
 *
 * Each building's width/height are randomized (not fixed squares) subject to
 * width * height == blockSize^2, so the "Obstacle size" control governs building
 * *area*, not a fixed square edge length.
 */
object MapGenerator {

    /**
     * Minimum empty-space gap (in grid cells) required between two non-overlapping
     * buildings for two agents to pass through side by side.
     *
     * Budget across the corridor's width: AGENT_CLEARANCE margin from the left wall,
     * AGENT_CLEARANCE margin from the right wall, and AGENT_CLEARANCE between the two
     * agents' centers -- that last figure isn't arbitrary, it's the same threshold
     * avoid_agent_collision() (Fluxotaxis.kt) uses to define a collision (two agents
     * closer than AGENT_CLEARANCE) and place_agents() uses for its own placement
     * conflict check. Sum: 3 * AGENT_CLEARANCE. (A single agent only needed the two
     * wall margins: 2 * AGENT_CLEARANCE.)
     */
    private val minPassableGap: Double get() = 3.0 * Scenario.AGENT_CLEARANCE

    private data class Rect(val row: Int, val col: Int, val h: Int, val w: Int)

    fun generate(
        seed: Long,
        dimension: Int,
        numBlocks: Int,
        blockSize: Int,
        maxAttemptsPerBlock: Int = 500,
    ): WorldMap {
        val map = WorldMap(dimension, dimension)
        val rnd = java.util.Random(seed)
        val placed = ArrayList<Rect>(numBlocks)

        /** Random width/height whose product is ~blockSize^2, aspect ratio drawn
         *  log-uniformly in [1/3, 3] so buildings range from tall-and-thin to
         *  wide-and-flat instead of always coming out square. */
        fun randomDimensions(): Pair<Int, Int> {
            val targetArea = blockSize.toDouble() * blockSize
            val logRatio = (rnd.nextDouble() * 2 - 1) * ln(3.0) // in [-ln3, +ln3]
            val ratio = exp(logRatio)                            // in [1/3, 3]
            var w = Math.round(Math.sqrt(targetArea * ratio)).toInt().coerceAtLeast(1)
            var h = Math.round(Math.sqrt(targetArea / ratio)).toInt().coerceAtLeast(1)
            val cap = max(1, dimension - 2)
            w = w.coerceAtMost(cap)
            h = h.coerceAtMost(cap)
            return w to h
        }

        fun randomRect(): Rect {
            val (w, h) = randomDimensions()
            val rowSpan = max(1, dimension - 1 - h)
            val colSpan = max(1, dimension - 1 - w)
            val row = 1 + (rnd.nextDouble() * rowSpan).toInt()
            val col = 1 + (rnd.nextDouble() * colSpan).toInt()
            return Rect(row, col, h, w)
        }

        for (block in 0 until numBlocks) {
            var candidate: Rect? = null
            var attempts = 0
            while (candidate == null && attempts < maxAttemptsPerBlock) {
                attempts++
                val r = randomRect()
                if (placed.all { isCompatible(r, it) }) candidate = r
            }
            // best-effort fallback: after exhausting attempts (dense/crowded config),
            // place it anyway rather than hanging or silently dropping a requested building.
            val chosen = candidate ?: randomRect()
            placed.add(chosen)

            for (x in chosen.row until chosen.row + chosen.h) {
                for (y in chosen.col until chosen.col + chosen.w) {
                    if (x in 0 until dimension && y in 0 until dimension) {
                        map.world[x * dimension + y] = MAP_SYM_BLOCKED
                    }
                }
            }
        }
        return map
    }

    /** True if `a` and `b` either overlap (fine -- they just merge into one obstacle)
     *  or are far enough apart to leave a passable gap. */
    private fun isCompatible(a: Rect, b: Rect): Boolean {
        val rowGap = axisGap(a.row, a.h, b.row, b.h)
        val colGap = axisGap(a.col, a.w, b.col, b.w)
        val overlapping = rowGap <= 0 && colGap <= 0
        if (overlapping) return true

        val gap = when {
            rowGap <= 0 -> colGap.toDouble()                       // rows overlap: straight corridor along columns
            colGap <= 0 -> rowGap.toDouble()                       // cols overlap: straight corridor along rows
            else -> hypot(rowGap.toDouble(), colGap.toDouble())    // staggered corners: diagonal gap
        }
        return gap >= minPassableGap
    }

    /** Gap between two 1D intervals [startA, startA+lenA) and [startB, startB+lenB);
     *  <= 0 means they overlap on this axis. */
    private fun axisGap(startA: Int, lenA: Int, startB: Int, lenB: Int): Int {
        val endA = startA + lenA
        val endB = startB + lenB
        return when {
            endA <= startB -> startB - endA
            endB <= startA -> startA - endB
            else -> -1
        }
    }

    /** Port of the emitter-placement safety check from PlumeGen.c's read_map_file(). */
    fun isValidEmitterLocation(map: WorldMap, ex: Int, ey: Int): Boolean {
        val exD = ex.toDouble(); val eyD = ey.toDouble()
        if (!map.isWorldRegionSquare(exD, eyD, Scenario.MIN_EMITTER_CLEARANCE)) return false
        if (map.isBlockedRegionSquare(exD, eyD, Scenario.MIN_EMITTER_CLEARANCE)) return false
        return true
    }
}
