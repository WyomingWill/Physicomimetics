package com.example.combinedsimulations.perfect

import kotlin.math.*
import kotlin.random.Random

// Each particle's ideal lattice slot (both square and triangular) is
// computed once from its creation-order id, matching NetLogo's
// init-m-n. Unlike the other two formation models, this one actively
// steers each particle toward its assigned slot rather than letting a
// lattice emerge purely from local distance rules.
class Particle(var x: Float, var y: Float, val color: Int, val id: Int) {
    var vx = 0f
    var vy = 0f
    var deltax = 0f
    var deltay = 0f

    val squareM: Int
    val squareN: Int
    val hexM: Int
    val hexN: Int

    init {
        val mn = initMN(id)
        squareM = mn[0]
        squareN = mn[1]
        hexM = mn[2]
        hexN = mn[3]
    }

    companion object {
        fun initMN(who: Int): IntArray {
            val ring = 1 + floor(sqrt(who / 4.0)).toInt()
            val index = who - (ring - 1) * (ring - 1) * 4

            val squareM: Int = when {
                index < (2 * ring - 1) -> 1 + index - ring
                index <= (4 * ring - 2) -> ring
                index < (6 * ring - 3) -> 5 * ring - index - 2
                else -> 1 - ring
            }
            val squareN: Int = when {
                index < (2 * ring) -> ring - 1
                index < (4 * ring - 2) -> 3 * ring - 2 - index
                index < (6 * ring - 2) -> -ring
                else -> index - 7 * ring + 3
            }

            val hRing: Int = when {
                who == 0 -> 1
                who < 7 -> 2
                who < 19 -> 3
                who < 37 -> 4
                who < 61 -> 5
                who < 91 -> 6
                else -> 7
            }
            val hIndex: Int = if (who == 0) 0 else who - ((3 * hRing * hRing) - (9 * hRing) + 7)

            val hexM: Int = when {
                hIndex < hRing -> 2 * hIndex - (hRing - 1)
                (3 * hRing - 3) <= hIndex && hIndex <= (4 * hRing - 4) -> (8 * hRing) - 8 - (2 * hIndex) - (hRing - 1)
                hRing <= hIndex && hIndex < (2 * hRing - 2) -> hIndex
                hIndex == (2 * hRing - 2) -> 2 * hRing - 2
                (2 * hRing - 2) < hIndex && hIndex < (3 * hRing - 3) -> (4 * hRing) - hIndex - 4
                (4 * hRing - 4) < hIndex && hIndex < (5 * hRing - 5) -> 3 * hRing - hIndex - 3
                (5 * hRing - 5) < hIndex -> hIndex - (7 * hRing) + 7
                hIndex == (5 * hRing - 5) -> 2 - 2 * hRing
                else -> 0
            }
            val hexN: Int = when {
                hIndex < hRing -> hRing - 1
                (3 * hRing - 3) <= hIndex && hIndex <= (4 * hRing - 4) -> 1 - hRing
                hRing <= hIndex && hIndex < (3 * hRing - 3) -> 2 * hRing - hIndex - 2
                hIndex > (4 * hRing - 4) -> hIndex + 5 - 5 * hRing
                else -> 0
            }

            return intArrayOf(squareM, squareN, hexM, hexN)
        }
    }
}

class ParticleSimulation {
    var numberOfParticles = 100
    var gravitationalConstant = 1000f
    var power = 2.5f
    var forceMaximum = 1f
    var friction = 0.5f
    var timeStep = 1f
    var desiredSeparation = 25f

    var isSquareFormation = true
        private set

    private val particles = mutableListOf<Particle>()
    private var nextId = 0

    private val worldSize = 400f
    private val boundary = 200f

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

    val comTrail = mutableListOf<Pair<Float, Float>>()

    private val javaRandom = java.util.Random()

    fun setup(count: Int) {
        particles.clear()
        comTrail.clear()
        nextId = 0
        isSquareFormation = true

        for (i in 0 until count) {
            val x = javaRandom.nextGaussian().toFloat() * 20f
            val y = javaRandom.nextGaussian().toFloat() * 20f
            val color = if (nextId % 2 == 0) 0 else 1
            particles.add(Particle(x, y, color, nextId++))
        }

        updateCenterOfMass()
        recordTrail()
    }

    fun update() {
        for (p in particles) {
            apParticle(p)
        }
        for (p in particles) {
            move(p)
        }
        updateCenterOfMass()
        recordTrail()
        updateMomentum()
    }

    // Direction unit vectors here deliberately use the same (possibly
    // halved) r that the force-magnitude/threshold math uses, matching
    // NetLogo's shared mutable "r" turtle variable exactly - a genuine
    // difference from the other two formation models in this app,
    // which normalize direction by the raw distance instead.
    private fun apParticle(p: Particle) {
        var fx = 0f
        var fy = 0f
        p.vx *= (1 - friction)
        p.vy *= (1 - friction)

        for (other in particles) {
            if (other.id == p.id) continue

            val dx = other.x - p.x
            val dy = other.y - p.y
            var r = sqrt(dx * dx + dy * dy)
            if (r < 0.1f) r = 0.1f

            var view = 1.5f
            if (isSquareFormation) {
                if ((p.id % 2) == (other.id % 2)) {
                    view = 1.3f
                    r /= sqrt(2f)
                } else {
                    view = 1.7f
                }
            }

            val pm: Int; val pn: Int; val km: Int; val kn: Int; val cx: Float; val cy: Float
            if (isSquareFormation) {
                pm = p.squareM; pn = p.squareN; cx = 0.5f; cy = 0.5f
                km = other.squareM; kn = other.squareN
            } else {
                pm = p.hexM; pn = p.hexN; cx = 0.25f; cy = 0.433f
                km = other.hexM; kn = other.hexN
            }

            if (r < view * desiredSeparation) {
                var f = gravitationalConstant / r.pow(power)
                if (f > forceMaximum) f = forceMaximum

                val ux = dx / r
                val uy = dy / r

                when {
                    r > desiredSeparation -> {
                        fx += f * ux; fy += f * uy
                    }
                    (dx < 0 && pm < km && dy < 0 && pn < kn) || (dx > 0 && pm > km && dy > 0 && pn > kn) -> {
                        fx += 2f * f * ux; fy += 2f * f * uy
                    }
                    (dx < 0 && pm < km) || (dx > 0 && pm > km) -> {
                        fx += 2f * f * ux; fy += 2f * f * uy
                    }
                    (dy < 0 && pn < kn) || (dy > 0 && pn > kn) -> {
                        fx += 2f * f * ux; fy += 2f * f * uy
                    }
                    (pn == kn && abs(dy) > cy * desiredSeparation) || (pm == km && abs(dx) > cx * desiredSeparation) -> {
                        fx += 2f * f * ux; fy += 2f * f * uy
                    }
                    !isSquareFormation && pn != kn && abs(dy) < cy * desiredSeparation -> {
                        fy -= 3f * f * uy
                    }
                    !isSquareFormation && pm != km && abs(dx) < cx * desiredSeparation -> {
                        fx -= 3f * f * ux
                    }
                    else -> {
                        fx -= f * ux; fy -= f * uy
                    }
                }
            }
        }

        val dvx = timeStep * fx
        val dvy = timeStep * fy
        p.vx += dvx
        p.vy += dvy
        p.deltax = timeStep * p.vx
        p.deltay = timeStep * p.vy
    }

    private fun move(p: Particle) {
        p.x += p.deltax
        p.y += p.deltay
    }

    private fun updateCenterOfMass() {
        if (particles.isEmpty()) return
        centerOfMassX = particles.sumOf { it.x.toDouble() }.toFloat() / particles.size
        centerOfMassY = particles.sumOf { it.y.toDouble() }.toFloat() / particles.size
    }

    private fun recordTrail() {
        comTrail.add(centerOfMassX to centerOfMassY)
        if (comTrail.size > 500) comTrail.removeAt(0)
    }

    private fun updateMomentum() {
        totalLinearMomentumX = particles.sumOf { it.vx.toDouble() }.toFloat()
        totalLinearMomentumY = particles.sumOf { it.vy.toDouble() }.toFloat()

        totalAngularMomentum = 0f
        for (p in particles) {
            val leverArmX = p.x - centerOfMassX
            val leverArmY = p.y - centerOfMassY
            val leverArmR = sqrt(leverArmX * leverArmX + leverArmY * leverArmY)
            val v = sqrt(p.vx * p.vx + p.vy * p.vy)
            if (v > 0.01f && leverArmR > 0.01f) {
                val velocityAngle = atan2(p.vy, p.vx)
                val leverAngle = atan2(leverArmY, leverArmX)
                val theta = velocityAngle - leverAngle
                totalAngularMomentum += leverArmR * v * sin(theta)
            }
        }
    }

    fun toggleFormation() {
        isSquareFormation = !isSquareFormation
    }

    fun clearTrail() {
        comTrail.clear()
    }

    fun addParticle() {
        if (particles.isEmpty()) return
        val template = particles.random()
        val x = template.x + javaRandom.nextGaussian().toFloat() * (desiredSeparation / 2f)
        val y = template.y + javaRandom.nextGaussian().toFloat() * (desiredSeparation / 2f)
        val color = if (nextId % 2 == 0) 0 else 1
        particles.add(Particle(x, y, color, nextId++))
    }

    fun getParticles(): List<Particle> = particles
    fun getWorldSize() = worldSize
    fun getBoundary() = boundary
}
