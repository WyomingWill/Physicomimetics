package com.example.combinedsimulations.dinos

import kotlin.math.*
import kotlin.random.Random

class Drone(var x: Float, var y: Float, var heading: Float, val id: Int) {
    var vx = 0f
    var vy = 0f
    var deltax = 0f
    var deltay = 0f
}

class Dino(var x: Float, var y: Float, var heading: Float)

class DinosSimulation {
    var numberOfDrones = 7
    var numberOfDinos = 280
    var randomSeed = 79
    var forceMaximum = 1f
    var friction = 0.5f
    var timeStep = 0.5f
    var desiredSeparation = 4f
    var goalForce = 0.3f

    private var rng = Random(randomSeed)

    private val worldSize = 150f
    private val boundary = 75f
    private val gridSize = 151
    private val chemical = FloatArray(gridSize * gridSize)
    private val chemicalBuffer = FloatArray(gridSize * gridSize)

    val drones = mutableListOf<Drone>()
    val dinos = mutableListOf<Dino>()

    var goalEnabled = true
        private set
    var minimizeChemical = false
        private set
    var allDone = false
        private set
    private var totalChemical = 0.0

    private val apoF = 0.10f
    private var g = 0f

    val goalX = -worldSize / 2.5f
    val goalY = -worldSize / 2.5f

    val comTrail = mutableListOf<Pair<Float, Float>>()
    var comX = 0f
        private set
    var comY = 0f
        private set

    fun setup() {
        rng = Random(randomSeed)
        chemical.fill(0f)
        drones.clear()
        dinos.clear()
        comTrail.clear()
        allDone = false
        totalChemical = 0.0

        val baseX = worldSize / 2.5f
        val baseY = worldSize / 2.5f
        val offsets = arrayOf(
            0f to 0f,
            -1f to 1f,
            -2f to 0f,
            1f to 1f,
            -1f to -1f,
            1f to -1f,
            2f to 0f
        )
        for (i in 0 until numberOfDrones) {
            val (dx, dy) = offsets[i % offsets.size]
            val heading = rng.nextFloat() * 360f
            drones.add(Drone(baseX + dx, baseY + dy, heading, i))
        }

        for (i in 0 until numberOfDinos) {
            val x = rng.nextFloat() * worldSize - boundary
            val y = rng.nextFloat() * worldSize - boundary
            dinos.add(Dino(x, y, rng.nextFloat() * 360f))
        }

        updateCenterOfMass()
        comTrail.add(comX to comY)
    }

    fun dinoStep() {
        diffuseAndEvaporate()
        for (dino in dinos) {
            dinoLife(dino)
        }
    }

    fun moveDronesStep(): Boolean {
        if (allDone) return true

        g = 0.9f * forceMaximum * desiredSeparation.pow(2) / (2f * sqrt(3f))

        for (drone in drones) {
            apDrone(drone)
        }
        for (drone in drones) {
            drone.x += drone.deltax
            drone.y += drone.deltay
            if (drone.x < -boundary) drone.x = -boundary
            if (drone.x > boundary) drone.x = boundary
            if (drone.y < -boundary) drone.y = -boundary
            if (drone.y > boundary) drone.y = boundary
        }

        updateCenterOfMass()
        comTrail.add(comX to comY)

        return allDone
    }

    private fun apDrone(drone: Drone) {
        var fx = 0f
        var fy = 0f
        drone.vx *= (1 - friction)
        drone.vy *= (1 - friction)
        var ncount = 0

        for (other in drones) {
            if (other.id == drone.id) continue
            val dx = other.x - drone.x
            val dy = other.y - drone.y
            val r = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)

            if (r < 1.5f * desiredSeparation) {
                ncount++
                var f = g / (r * r)
                if (f > forceMaximum) f = forceMaximum
                if (r > desiredSeparation) {
                    fx += f * (dx / r)
                    fy += f * (dy / r)
                } else {
                    fx -= f * (dx / r)
                    fy -= f * (dy / r)
                }

                val myChem = sensedChemical(drone.x, drone.y)
                val otherChem = sensedChemical(other.x, other.y)
                val avoid = (minimizeChemical && myChem < otherChem) ||
                    (!minimizeChemical && myChem > otherChem)
                if (avoid) {
                    fx -= apoF * (dx / r)
                    fy -= apoF * (dy / r)
                } else {
                    fx += apoF * (dx / r)
                    fy += apoF * (dy / r)
                }
            }
        }

        if (goalEnabled && ncount > 0) {
            val dx = goalX - drone.x
            val dy = goalY - drone.y
            val r = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)
            if (r < 2f) allDone = true
            fx += goalForce * (dx / r)
            fy += goalForce * (dy / r)
        }

        totalChemical += sensedChemical(drone.x, drone.y)

        val dvx = timeStep * fx
        val dvy = timeStep * fy
        drone.vx += dvx
        drone.vy += dvy
        drone.deltax = timeStep * drone.vx
        drone.deltay = timeStep * drone.vy
    }

    private fun dinoLife(dino: Dino) {
        val ahead = sampleChemical(dino.x, dino.y, dino.heading, 1f)
        val right = sampleChemical(dino.x, dino.y, dino.heading - 45f, 1f)
        val left = sampleChemical(dino.x, dino.y, dino.heading + 45f, 1f)

        if (right >= ahead && right >= left) {
            dino.heading -= 45f
        } else if (left >= ahead) {
            dino.heading += 45f
        }

        dino.heading -= rng.nextFloat() * 40f
        dino.heading += rng.nextFloat() * 40f

        val radians = Math.toRadians(dino.heading.toDouble())
        dino.x += cos(radians).toFloat()
        dino.y += sin(radians).toFloat()
        if (dino.x < -boundary) dino.x = -boundary
        if (dino.x > boundary) dino.x = boundary
        if (dino.y < -boundary) dino.y = -boundary
        if (dino.y > boundary) dino.y = boundary

        depositChemical(dino.x, dino.y, 2f)
    }

    private fun cellIndex(x: Float, y: Float): Int {
        var col = Math.round(x) + boundary.toInt()
        var row = Math.round(y) + boundary.toInt()
        if (col < 0) col = 0
        if (col >= gridSize) col = gridSize - 1
        if (row < 0) row = 0
        if (row >= gridSize) row = gridSize - 1
        return row * gridSize + col
    }

    private fun sensedChemical(x: Float, y: Float): Float = chemical[cellIndex(x, y)]

    private fun sampleChemical(x: Float, y: Float, headingDeg: Float, dist: Float): Float {
        val radians = Math.toRadians(headingDeg.toDouble())
        val sx = x + dist * cos(radians).toFloat()
        val sy = y + dist * sin(radians).toFloat()
        return chemical[cellIndex(sx, sy)]
    }

    private fun depositChemical(x: Float, y: Float, amount: Float) {
        chemical[cellIndex(x, y)] += amount
    }

    private fun diffuseAndEvaporate() {
        chemicalBuffer.fill(0f)
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val idx = row * gridSize + col
                val v = chemical[idx]
                if (v == 0f) continue
                val share = v / 8f
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = row + dr
                        val nc = col + dc
                        if (nr in 0 until gridSize && nc in 0 until gridSize) {
                            chemicalBuffer[nr * gridSize + nc] += share
                        } else {
                            chemicalBuffer[idx] += share
                        }
                    }
                }
            }
        }
        System.arraycopy(chemicalBuffer, 0, chemical, 0, chemical.size)
        for (i in chemical.indices) {
            chemical[i] *= 0.995f
        }
    }

    private fun updateCenterOfMass() {
        if (drones.isEmpty()) return
        comX = drones.sumOf { it.x.toDouble() }.toFloat() / drones.size
        comY = drones.sumOf { it.y.toDouble() }.toFloat() / drones.size
    }

    fun toggleGoal() {
        goalEnabled = !goalEnabled
    }

    fun toggleMinMax() {
        minimizeChemical = !minimizeChemical
    }

    fun averageChemicalSeen(): Double =
        if (drones.isEmpty()) 0.0 else totalChemical / drones.size

    fun getChemicalGrid(): FloatArray = chemical
    fun getGridSize() = gridSize
    fun getWorldSize() = worldSize
    fun getBoundary() = boundary
}
