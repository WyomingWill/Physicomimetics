package com.example.combinedsimulations.coverage

import kotlin.math.*
import kotlin.random.Random

data class Agent(
    var x: Float,
    var y: Float,
    var heading: Float,
    var steps: Int = 0,
    val id: Int
)

class UniformCoverageSimulation {
    private val agents = mutableListOf<Agent>()
    private val cellCounts = IntArray(9)

    var meanFreePathLength = 15
    var ticks: Long = 0
        private set

    private var nextId = 0

    private val worldSize = 84f
    private val boundary = 40f
    private val b1 = ((2 * boundary) / 3f) - boundary
    private val b2 = ((4 * boundary) / 3f) - boundary

    fun setup(numParticles: Int) {
        agents.clear()
        cellCounts.fill(0)
        ticks = 0
        nextId = 0

        for (i in 0 until numParticles) {
            val x = (Random.nextFloat() * 2 - 1) * (15f * boundary / 16f)
            val y = (Random.nextFloat() * 2 - 1) * (15f * boundary / 16f)
            val heading = Random.nextFloat() * 360f
            agents.add(Agent(x, y, heading, 0, nextId++))
            monitorAgent(agents.last())
        }
    }

    fun update() {
        ticks++
        for (agent in agents) {
            moveAgent(agent)
        }
    }

    private fun moveAgent(agent: Agent) {
        var tries = 1
        agent.steps++

        while (tries < 10 && shouldTurn(agent)) {
            agent.heading = Random.nextFloat() * 360f
            agent.steps = 0
            tries++
        }

        if (tries < 10) {
            val radians = Math.toRadians(agent.heading.toDouble())
            agent.x += cos(radians).toFloat()
            agent.y += sin(radians).toFloat()

            if (agent.x < -boundary) agent.x = -boundary
            if (agent.x > boundary) agent.x = boundary
            if (agent.y < -boundary) agent.y = -boundary
            if (agent.y > boundary) agent.y = boundary

            monitorAgent(agent)
        }
    }

    private fun shouldTurn(agent: Agent): Boolean {
        if (agent.steps >= meanFreePathLength) return true

        val radians = Math.toRadians(agent.heading.toDouble())
        val lookAheadDistance = 0.0001f
        var lookAheadX = agent.x + cos(radians).toFloat() * lookAheadDistance
        var lookAheadY = agent.y + sin(radians).toFloat() * lookAheadDistance

        if (abs(lookAheadX) >= boundary || abs(lookAheadY) >= boundary) {
            return true
        }

        for (other in agents) {
            if (other.id == agent.id) continue

            val dx = other.x - agent.x
            val dy = other.y - agent.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < 3f) {
                val angleToOther = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                var angleDiff = angleToOther - agent.heading
                angleDiff = (angleDiff % 360f + 360f) % 360f
                if (angleDiff < 30f) {
                    return true
                }
            }
        }

        return false
    }

    private fun monitorAgent(agent: Agent) {
        val cellX = when {
            agent.x < b1 -> 0
            agent.x <= b2 -> 1
            else -> 2
        }

        val cellY = when {
            agent.y < b1 -> 0
            agent.y <= b2 -> 1
            else -> 2
        }

        val cellIndex = cellX + (3 * cellY)
        cellCounts[cellIndex]++
    }

    fun getAgents() = agents.toList()
    fun getCellCounts() = cellCounts.clone()

    fun getWorldSize() = worldSize
    fun getBoundary() = boundary
    fun getB1() = b1
    fun getB2() = b2

    fun getUniformityDeviation(): Double {
        if (ticks == 0L || agents.isEmpty()) return 0.0

        val totalVisits = (ticks * agents.size).toDouble()
        val idealProportion = 1.0 / 9.0

        var sumSquaredDiff = 0.0
        for (count in cellCounts) {
            val proportion = count / totalVisits
            val diff = proportion - idealProportion
            sumSquaredDiff += diff * diff
        }

        return sqrt(sumSquaredDiff)
    }

    fun getTheoreticalMeanFreePath(): Double {
        return 0.65 * (2.0 * boundary / 3.0)
    }

    fun getCellColor(cellIndex: Int): Int {
        return when (cellIndex) {
            0, 2, 6, 8 -> 0
            4 -> 2
            else -> 1
        }
    }
}
