package com.example.combinedsimulations.particle

import kotlin.math.*
import kotlin.random.Random

data class SimulationParams(
    var lennardJonesConstant: Float = 10f,
    var power: Float = 6f,
    var forceMaximum: Float = 1f,
    var friction: Float = 0.5f,
    var timeStep: Float = 1f,
    var desiredSeparation: Float = 20f,
    var goalForce: Float = 0.25f,
    var obstacleSize: Float = 10f
)

data class Vector2(var x: Float = 0f, var y: Float = 0f)

sealed class Entity(
    var position: Vector2,
    var velocity: Vector2 = Vector2(),
    var mass: Float = 1f,
    var id: Int = 0
) {
    var force = Vector2()
    var deltax = 0f
    var deltay = 0f
}

class Particle(position: Vector2, val color: Int, id: Int) : Entity(position, Vector2(), 1f, id)
class Goal(position: Vector2, id: Int) : Entity(position, Vector2(), 100000f, id)
class Obstacle(position: Vector2, id: Int) : Entity(position, Vector2(), 100000f, id)

class ParticleSimulation {
    val params = SimulationParams()
    private val particles = mutableListOf<Particle>()
    private val goals = mutableListOf<Goal>()
    private val obstacles = mutableListOf<Obstacle>()

    var isSquareFormation = false
        private set
    var isGoalEnabled = false
        private set
    var disabledCount = 0
        private set

    private var nextId = 0
    private val worldWidth = 700f
    private val worldHeight = 400f

    var centerOfMassX = 0f
        private set
    var centerOfMassY = 0f
        private set
    var totalLinearMomentumX = 0f
        private set
    var totalLinearMomentumY = 0f
        private set
    var totalAngularMomentum = 0f
        private set

    fun setup(numParticles: Int) {
        particles.clear()
        obstacles.clear()
        goals.clear()
        disabledCount = 0
        nextId = 0

        for (i in 0 until numParticles) {
            val x = worldWidth / 3 + Random.nextFloat() * 40 - 20
            val y = Random.nextFloat() * 40 - 20
            val color = if (i % 2 == 0) 0 else 1
            particles.add(Particle(Vector2(x, y), color, nextId++))
        }

        goals.add(Goal(Vector2(-worldWidth / 3, 0f), nextId++))

        updateCenterOfMass()
    }

    fun update() {
        updateParticles()
        updateGoals()
        updateObstacles()
        moveAll()
        updateCenterOfMass()
        updateMomentum()
    }

    private fun updateParticles() {
        for (p in particles) {
            p.force.x = 0f
            p.force.y = 0f
            p.velocity.x *= (1 - params.friction)
            p.velocity.y *= (1 - params.friction)

            for (other in particles) {
                if (p.id == other.id) continue

                val dx = other.position.x - p.position.x
                val dy = other.position.y - p.position.y
                var r = sqrt(dx * dx + dy * dy)
                if (r < 0.1f) r = 0.1f

                var view = 1.5f
                var modifiedR = r

                if (isSquareFormation) {
                    if ((p.id % 2) == (other.id % 2)) {
                        view = 1.3f
                        modifiedR = r / sqrt(2f)
                    } else {
                        view = 1.7f
                    }
                }

                if (modifiedR < view * params.desiredSeparation) {
                    val dp2 = params.desiredSeparation.pow(2 * params.power)
                    val rp1 = modifiedR.pow(2 * params.power + 1)
                    val dp = params.desiredSeparation.pow(params.power)
                    val rp = modifiedR.pow(params.power + 1)

                    var f = params.lennardJonesConstant * ((dp2 / rp1) - (dp / rp))
                    if (f > params.forceMaximum) f = params.forceMaximum

                    p.force.x -= f * (dx / r)
                    p.force.y -= f * (dy / r)
                }
            }

            for (obs in obstacles) {
                val dx = obs.position.x - p.position.x
                val dy = obs.position.y - p.position.y
                val r = sqrt(dx * dx + dy * dy)

                if (r <= params.obstacleSize) {
                    val f = params.obstacleSize - r
                    p.force.x -= f * (dx / r)
                    p.force.y -= f * (dy / r)
                }
            }

            if (isGoalEnabled) {
                for (goal in goals) {
                    val dx = goal.position.x - p.position.x
                    val dy = goal.position.y - p.position.y
                    val r = sqrt(dx * dx + dy * dy)
                    if (r > 0.1f) {
                        val f = params.goalForce
                        p.force.x += f * (dx / r)
                        p.force.y += f * (dy / r)
                    }
                }
            }

            val dvx = params.timeStep * (p.force.x / p.mass)
            val dvy = params.timeStep * (p.force.y / p.mass)
            p.velocity.x += dvx
            p.velocity.y += dvy

            p.deltax = params.timeStep * p.velocity.x
            p.deltay = params.timeStep * p.velocity.y
        }
    }

    private fun updateGoals() {
        for (goal in goals) {
            goal.force.x = 0f
            goal.force.y = 0f
            goal.velocity.x *= (1 - params.friction)
            goal.velocity.y *= (1 - params.friction)

            for (p in particles) {
                val dx = p.position.x - goal.position.x
                val dy = p.position.y - goal.position.y
                val r = sqrt(dx * dx + dy * dy)
                if (r > 0.1f) {
                    val f = params.goalForce
                    goal.force.x += f * (dx / r)
                    goal.force.y += f * (dy / r)
                }
            }

            val dvx = params.timeStep * (goal.force.x / goal.mass)
            val dvy = params.timeStep * (goal.force.y / goal.mass)
            goal.velocity.x += dvx
            goal.velocity.y += dvy

            goal.deltax = params.timeStep * goal.velocity.x
            goal.deltay = params.timeStep * goal.velocity.y
        }
    }

    private fun updateObstacles() {
        for (obs in obstacles) {
            obs.force.x = 0f
            obs.force.y = 0f
            obs.velocity.x *= (1 - params.friction)
            obs.velocity.y *= (1 - params.friction)

            for (p in particles) {
                val dx = p.position.x - obs.position.x
                val dy = p.position.y - obs.position.y
                val r = sqrt(dx * dx + dy * dy)

                if (r <= params.obstacleSize) {
                    val f = params.obstacleSize - r
                    obs.force.x -= f * (dx / r)
                    obs.force.y -= f * (dy / r)
                }
            }

            val dvx = params.timeStep * (obs.force.x / obs.mass)
            val dvy = params.timeStep * (obs.force.y / obs.mass)
            obs.velocity.x += dvx
            obs.velocity.y += dvy

            obs.deltax = params.timeStep * obs.velocity.x
            obs.deltay = params.timeStep * obs.velocity.y
        }
    }

    private fun moveAll() {
        for (p in particles) {
            p.position.x += p.deltax
            p.position.y += p.deltay
        }
        for (g in goals) {
            g.position.x += g.deltax
            g.position.y += g.deltay
        }
        for (o in obstacles) {
            o.position.x += o.deltax
            o.position.y += o.deltay
        }
    }

    private fun updateCenterOfMass() {
        val entities = if (isGoalEnabled) {
            particles + goals + obstacles
        } else {
            particles + obstacles
        }

        if (entities.isEmpty()) return

        var totalMassX = 0f
        var totalMassY = 0f
        var totalMass = 0f

        for (e in entities) {
            totalMassX += e.position.x * e.mass
            totalMassY += e.position.y * e.mass
            totalMass += e.mass
        }

        centerOfMassX = totalMassX / totalMass
        centerOfMassY = totalMassY / totalMass
    }

    private fun updateMomentum() {
        val entities = if (isGoalEnabled) {
            particles + goals + obstacles
        } else {
            particles + obstacles
        }

        totalLinearMomentumX = entities.sumOf { (it.mass * it.velocity.x).toDouble() }.toFloat()
        totalLinearMomentumY = entities.sumOf { (it.mass * it.velocity.y).toDouble() }.toFloat()

        totalAngularMomentum = 0f
        for (e in entities) {
            val leverArmX = e.position.x - centerOfMassX
            val leverArmY = e.position.y - centerOfMassY
            val leverArmR = sqrt(leverArmX * leverArmX + leverArmY * leverArmY)

            val v = sqrt(e.velocity.x * e.velocity.x + e.velocity.y * e.velocity.y)
            if (v > 0.01f && leverArmR > 0.01f) {
                val velocityAngle = atan2(e.velocity.y, e.velocity.x)
                val leverAngle = atan2(leverArmY, leverArmX)
                val theta = velocityAngle - leverAngle
                totalAngularMomentum += leverArmR * e.mass * v * sin(theta)
            }
        }
    }

    fun toggleFormation() {
        isSquareFormation = !isSquareFormation
        updateCenterOfMass()
        updateMomentum()
    }

    fun toggleGoal() {
        isGoalEnabled = !isGoalEnabled
        updateCenterOfMass()
        updateMomentum()
    }

    fun addObstacle(x: Float, y: Float) {
        obstacles.add(Obstacle(Vector2(x, y), nextId++))
        updateCenterOfMass()
        updateMomentum()
    }

    fun clearObstacles() {
        obstacles.clear()
        updateCenterOfMass()
        updateMomentum()
    }

    fun addParticle() {
        if (particles.isEmpty()) return
        val template = particles.random()
        val x = template.position.x + Random.nextFloat() * params.desiredSeparation / 2 - params.desiredSeparation / 4
        val y = template.position.y + Random.nextFloat() * params.desiredSeparation / 2 - params.desiredSeparation / 4
        val color = if (nextId % 2 == 0) 0 else 1
        particles.add(Particle(Vector2(x, y), color, nextId++))
        updateCenterOfMass()
        updateMomentum()
    }

    fun removeParticle() {
        val activeParticles = particles.filter { it.mass == 1f }
        if (activeParticles.size > 1) {
            particles.remove(activeParticles.random())
            updateCenterOfMass()
            updateMomentum()
        }
    }

    fun disableParticle() {
        val activeParticles = particles.filter { it.mass == 1f }
        if (activeParticles.size > 1) {
            val p = activeParticles.random()
            p.mass = 100000f
            disabledCount++
            updateCenterOfMass()
            updateMomentum()
        }
    }

    fun getParticles() = particles.toList()
    fun getGoals() = goals.toList()
    fun getObstacles() = obstacles.toList()
    fun getWorldWidth() = worldWidth
    fun getWorldHeight() = worldHeight
}
