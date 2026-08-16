package com.example.combinedsimulations.kinetic

import kotlin.math.*
import kotlin.random.Random

data class KineticAgent(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val id: Int
)

class KineticTheorySimulation {
    private val agents = mutableListOf<KineticAgent>()
    private val velHist = FloatArray(13)
    private var sample = 0

    var wallVelocity = 1.0f
    var temperature = 0.01f

    var slope = 0.0
        private set
    var correlation = 0.0
        private set

    private var nextId = 0
    private val worldSize = 84f
    private val boundary = 42f
    private val wallZoneWidth = 3f
    private val javaRandom = java.util.Random()

    private val cellSize = 2f
    private val gridCols = ceil((2f * boundary) / cellSize).toInt()
    private val gridRows = gridCols
    private val grid: Array<MutableList<KineticAgent>> = Array(gridCols * gridRows) { mutableListOf() }

    private var order = IntArray(0)

    fun setup(numParticles: Int) {
        agents.clear()
        velHist.fill(0f)
        sample = 0
        nextId = 0

        for (i in 0 until numParticles) {
            val heading = Random.nextFloat() * 360f
            val v = sqrt(temperature) * 1.2533f
            val radians = Math.toRadians(heading.toDouble())
            val vx = (v * cos(radians)).toFloat()
            val vy = (v * sin(radians)).toFloat()
            val x = (Random.nextFloat() * 2 - 1) * (7f * boundary / 8f)
            val y = (Random.nextFloat() * 2 - 1) * (7f * boundary / 8f)
            agents.add(KineticAgent(x, y, vx, vy, nextId++))
        }

        order = IntArray(agents.size) { it }

        monitor()
        computeStats()
    }

    fun update() {
        buildGrid()
        shuffleOrder()
        for (idx in order) {
            val agent = agents[idx]
            applyCollision(agent)
            applyWalls(agent)
        }
        for (agent in agents) {
            move(agent)
        }
        monitor()
        computeStats()
    }

    private fun shuffleOrder() {
        for (i in order.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }
    }

    private fun colIndex(x: Float): Int {
        var idx = floor((x + boundary) / cellSize).toInt()
        if (idx < 0) idx = 0
        if (idx >= gridCols) idx = gridCols - 1
        return idx
    }

    private fun rowIndex(y: Float): Int {
        var idx = floor((y + boundary) / cellSize).toInt()
        if (idx < 0) idx = 0
        if (idx >= gridRows) idx = gridRows - 1
        return idx
    }

    private fun buildGrid() {
        for (cell in grid) cell.clear()
        for (agent in agents) {
            val col = colIndex(agent.x)
            val row = rowIndex(agent.y)
            grid[row * gridCols + col].add(agent)
        }
    }

    private fun applyCollision(agent: KineticAgent) {
        val baseCol = colIndex(agent.x)
        val baseRow = rowIndex(agent.y)
        var chosen: KineticAgent? = null
        var seen = 0

        for (dc in -1..1) {
            val col = baseCol + dc
            if (col < 0 || col >= gridCols) continue
            for (dr in -1..1) {
                val row = baseRow + dr
                if (row < 0 || row >= gridRows) continue
                val cellAgents = grid[row * gridCols + col]
                for (other in cellAgents) {
                    if (other.id == agent.id) continue
                    val dx = other.x - agent.x
                    val dy = other.y - agent.y
                    if (dx * dx + dy * dy >= 4f) continue
                    seen++
                    if (Random.nextInt(seen) == 0) chosen = other
                }
            }
        }

        val friend = chosen ?: return
        val dvx = agent.vx - friend.vx
        val dvy = agent.vy - friend.vy
        val relSpeed = sqrt(dvx * dvx + dvy * dvy)
        val cmVelX = 0.5f * (agent.vx + friend.vx)
        val cmVelY = 0.5f * (agent.vy + friend.vy)
        val theta = Random.nextFloat() * 360f
        val radians = Math.toRadians(theta.toDouble())
        val costh = cos(radians).toFloat()
        val sinth = sin(radians).toFloat()
        val vrelX = relSpeed * sinth
        val vrelY = relSpeed * costh

        agent.vx = cmVelX + 0.5f * vrelX
        agent.vy = cmVelY + 0.5f * vrelY
        friend.vx = cmVelX - 0.5f * vrelX
        friend.vy = cmVelY - 0.5f * vrelY
    }

    private fun applyWalls(agent: KineticAgent) {
        if (agent.x >= boundary - wallZoneWidth) {
            val u = Random.nextFloat().coerceIn(0.0001f, 0.9999f)
            agent.vx = -(sqrt(2f * temperature) * sqrt(-ln(u)))
            agent.vy = (nextGaussian() * sqrt(temperature)) + wallVelocity
        }
        if (agent.x <= -boundary + wallZoneWidth) {
            val u = Random.nextFloat().coerceIn(0.0001f, 0.9999f)
            agent.vx = sqrt(2f * temperature) * sqrt(-ln(u))
            agent.vy = (nextGaussian() * sqrt(temperature)) - wallVelocity
        }
    }

    private fun nextGaussian(): Float = javaRandom.nextGaussian().toFloat()

    private fun move(agent: KineticAgent) {
        agent.x += agent.vx
        agent.y += agent.vy

        if (agent.x < -boundary) agent.x = -boundary
        if (agent.x > boundary) agent.x = boundary

        if (agent.y > boundary) agent.y -= worldSize
        if (agent.y < -boundary) agent.y += worldSize
    }

    private fun monitor() {
        val counts = IntArray(13)
        val sums = FloatArray(13)
        for (agent in agents) {
            var col = (((agent.x + boundary) / worldSize) * 13).toInt()
            if (col < 0) col = 0
            if (col > 12) col = 12
            counts[col]++
            sums[col] += agent.vy
        }
        for (i in 0..12) {
            if (counts[i] > 0) {
                velHist[i] += sums[i] / counts[i]
            }
        }
        sample++
    }

    fun resample() {
        velHist.fill(0f)
        sample = 0
    }

    private fun computeStats() {
        if (sample == 0) { slope = 0.0; correlation = 0.0; return }
        val xbar = 6.0
        var ybar = 0.0
        for (i in 0..12) ybar += (velHist[i] / sample)
        ybar /= 13.0

        var sxy = 0.0
        var sx = 0.0
        var sy = 0.0
        for (i in 0..12) {
            val yi = velHist[i] / sample
            val dxi = i - xbar
            val dyi = yi - ybar
            sxy += dxi * dyi
            sx += dxi * dxi
            sy += dyi * dyi
        }
        slope = if (sx != 0.0) sxy / sx else 0.0
        correlation = if (sx > 0.0 && sy > 0.0) sxy / (sqrt(sx) * sqrt(sy)) else 0.0
    }

    fun getAgents(): List<KineticAgent> = agents

    fun getVelocityHistogram(): FloatArray {
        if (sample == 0) return FloatArray(13)
        return FloatArray(13) { velHist[it] / sample }
    }

    fun getSampleCount() = sample
    fun getMeanVx(): Double = if (agents.isEmpty()) 0.0 else agents.sumOf { it.vx.toDouble() } / agents.size
    fun getMeanVy(): Double = if (agents.isEmpty()) 0.0 else agents.sumOf { it.vy.toDouble() } / agents.size
    fun getWorldSize() = worldSize
    fun getBoundary() = boundary
    fun getWallZoneWidth() = wallZoneWidth
}
