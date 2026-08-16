package com.example.combinedsimulations.apo

import kotlin.math.*
import kotlin.random.Random

class ApoRobot(var x: Float, var y: Float, val id: Int) {
    var vx = 0f
    var vy = 0f
    var deltax = 0f
    var deltay = 0f
}

class ApoSimulation {
    var numberOfRobots = 7
    var forceMaximum = 1f
    var friction = 0.5f
    var timeStep = 1f
    var desiredSeparation = 50f
    var noise = 0f
        private set

    private val apoF = 0.10f
    private var g = 0f

    private val worldSize = 500f
    private val boundary = 250f
    private val gridSize = 501
    private val gridHalf = 250

    private val fitness = FloatArray(gridSize * gridSize)
    var fitnessMin = 0f
        private set
    var fitnessMax = 1f
        private set
    var fitnessVersion = 0
        private set

    var showFitness = true
        private set

    var zoom = 1
        private set

    var optX = -125f
        private set
    var optY = -125f
        private set

    val robots = mutableListOf<ApoRobot>()

    var centerOfMassX = 0f
        private set
    var centerOfMassY = 0f
        private set
    var apomin = 0.0
        private set

    val comTrail = mutableListOf<Pair<Float, Float>>()
    var trailMinDistance = 0f

    private val javaRandom = java.util.Random()

    fun setup(count: Int) {
        numberOfRobots = count
        robots.clear()
        comTrail.clear()
        optX = -125f
        optY = -125f
        zoom = 1

        for (i in 0 until count) {
            val x = worldSize / 3f + javaRandom.nextGaussian().toFloat()
            val y = worldSize / 3f + javaRandom.nextGaussian().toFloat()
            robots.add(ApoRobot(x, y, i))
        }

        updateCenterOfMass()
        calculatePatches()
    }

    fun step() {
        g = 0.9f * forceMaximum * desiredSeparation.pow(2) / (2f * sqrt(3f))

        for (robot in robots) {
            updateRobot(robot)
        }
        for (robot in robots) {
            robot.x = (robot.x + robot.deltax).coerceIn(-boundary, boundary)
            robot.y = (robot.y + robot.deltay).coerceIn(-boundary, boundary)
        }

        updateCenterOfMass()
        val dx = (centerOfMassX - optX).toDouble()
        val dy = (centerOfMassY - optY).toDouble()
        apomin = sqrt(dx * dx + dy * dy)

        val last = comTrail.lastOrNull()
        if (last == null) {
            comTrail.add(centerOfMassX to centerOfMassY)
        } else {
            val ddx = centerOfMassX - last.first
            val ddy = centerOfMassY - last.second
            if (ddx * ddx + ddy * ddy > trailMinDistance * trailMinDistance) {
                comTrail.add(centerOfMassX to centerOfMassY)
                // Was 500. Recording stays at 1 dot per screen pixel of movement
                // (ApoActivity's trailMinDistance, unchanged); this cap alone is what
                // delivers ~10x more dots, by letting the trail remember 10x more
                // travel distance before old points get evicted.
                if (comTrail.size > 5000) comTrail.removeAt(0)
            }
        }
    }

    private fun updateRobot(robot: ApoRobot) {
        var fx = 0f
        var fy = 0f
        robot.vx *= (1 - friction)
        robot.vy *= (1 - friction)

        val myFitness = rastrigan(robot.x, robot.y)

        for (other in robots) {
            if (other.id == robot.id) continue
            val dx = other.x - robot.x
            val dy = other.y - robot.y
            val r = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)

            if (r < 1.5f * desiredSeparation) {
                var f = g / (r * r)
                if (f > forceMaximum) f = forceMaximum
                if (r > desiredSeparation) {
                    fx += f * (dx / r)
                    fy += f * (dy / r)
                } else {
                    fx -= f * (dx / r)
                    fy -= f * (dy / r)
                }

                val otherFitness = rastrigan(other.x, other.y)
                if (myFitness < otherFitness) {
                    fx -= apoF * (dx / r)
                    fy -= apoF * (dy / r)
                } else {
                    fx += apoF * (dx / r)
                    fy += apoF * (dy / r)
                }
            }
        }

        val dvx = timeStep * fx
        val dvy = timeStep * fy
        robot.vx += dvx
        robot.vy += dvy
        robot.deltax = timeStep * robot.vx
        robot.deltay = timeStep * robot.vy
    }

    private fun updateCenterOfMass() {
        if (robots.isEmpty()) return
        centerOfMassX = robots.sumOf { it.x.toDouble() }.toFloat() / robots.size
        centerOfMassY = robots.sumOf { it.y.toDouble() }.toFloat() / robots.size
    }

    private fun rastrigan(x0: Float, y0: Float): Float {
        val x = x0 / zoom
        val y = y0 / zoom
        val ox = optX / zoom
        val oy = optY / zoom
        val dx = x - ox
        val dy = y - oy
        val tx = dx * dx - 10f * cos(Math.toRadians(360.0 * dx)).toFloat() + 10f
        val ty = dy * dy - 10f * cos(Math.toRadians(360.0 * dy)).toFloat() + 10f
        val noiseTerm = noise * (Random.nextFloat() - 0.5f)
        return tx + ty + noiseTerm
    }

    fun calculatePatches() {
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var idx = 0
        for (row in 0 until gridSize) {
            val py = (row - gridHalf).toFloat()
            for (col in 0 until gridSize) {
                val px = (col - gridHalf).toFloat()
                val v = rastrigan(px, py)
                fitness[idx] = v
                if (v < minV) minV = v
                if (v > maxV) maxV = v
                idx++
            }
        }
        fitnessMin = minV
        fitnessMax = maxV
        showFitness = true
        fitnessVersion++
    }

    fun clearPatches() {
        showFitness = false
    }

    fun zoomIn() {
        zoom *= 10
        calculatePatches()
    }

    fun resetZoom() {
        zoom = 1
        calculatePatches()
    }

    fun setNoise(value: Float) {
        if (value != noise) {
            noise = value
            calculatePatches()
        }
    }

    fun moveOptimum(x: Float, y: Float) {
        optX = x.coerceIn(-boundary, boundary)
        optY = y.coerceIn(-boundary, boundary)
        calculatePatches()
    }

    fun getFitnessGrid(): FloatArray = fitness
    fun getGridSize() = gridSize
    fun getWorldSize() = worldSize
    fun getBoundary() = boundary
}
