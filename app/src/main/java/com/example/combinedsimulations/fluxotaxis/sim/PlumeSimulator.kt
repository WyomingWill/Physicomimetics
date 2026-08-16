package com.example.combinedsimulations.fluxotaxis.sim

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Port of PlumeGen.c's `puff_t`. */
data class Puff(var x: Double, var y: Double, var r: Double, var t: Double)

/**
 * Real-time port of PlumeGen.c. The original wrote every VIS_REDUCTION-th step to disk;
 * here `step()` is called once per simulation tick and mutates u/v/rho in place so the
 * UI can read them directly for both rendering and agent sensing (same arrays TraceGen
 * would have read from the .plume file).
 */
class PlumeSimulator(val map: WorldMap, seed: Long = 2L) {
    val u = DoubleArray(map.nxy)
    val v = DoubleArray(map.nxy)
    val rho = DoubleArray(map.nxy)
    private val scratch = DoubleArray(map.nxy)

    private val rnd = Random(seed)
    private val puffs = ArrayList<Puff>()

    // COPY from Farrell's Integral.cpp: 4 boundary segments (top/bottom/left/right corners),
    // each with a 2-state colored-noise process, for both U and V components.
    private val xBoundary = Array(4) { doubleArrayOf(0.0, 0.0) }
    private val yBoundary = Array(4) { doubleArrayOf(0.0, 0.0) }

    init {
        for (i in 0 until map.nxy) { u[i] = Scenario.MEAN_U; v[i] = Scenario.MEAN_V }
        repeat(1 /* PREP_STEPS */) {
            computeVelocity()
            movePuffs()
        }
        computeDensity()
    }

    // ##### VELOCITY (wind field) #####

    /** Box-Muller normal RV, matching PlumeGen.c's nrand(). */
    private fun nrand(): Double {
        val a = rnd.nextDouble(); val b = rnd.nextDouble()
        return kotlin.math.sqrt(-2 * ln(a.coerceAtLeast(1e-12))) * cos(2 * Math.PI * b)
    }

    /**
     * Colored-noise boundary process, port of colored_noise(x, dt).
     *
     * The original was only ever forward-Euler integrated at a single fixed
     * WIND_BANDWIDTH=0.01, which happens to be small enough for a single dt=1 step to
     * stay stable. This port added a turbulence-speed slider that scales WIND_BANDWIDTH
     * up, and forward-Euler stability for this damped oscillator degrades as bandwidth
     * grows -- above roughly 15-20x the original speed it diverges to Infinity/NaN
     * within a few thousand steps, which was the actual cause of the app crashing
     * ("Offset is unspecified" in Compose -- NaN wind values propagating into arrow
     * coordinates). Fix: subdivide each dt=1 tick into enough smaller integration steps
     * to keep bandwidth*subDt pinned near the original's stable 0.01 regardless of how
     * high the slider goes, rather than capping the feature or letting it blow up.
     */
    private fun coloredNoise(x: DoubleArray, dt: Double): Double {
        val referenceBandwidthDtProduct = 0.01 // matches the original's stable default (WIND_BANDWIDTH * DEL_T)
        val substeps = max(1, Math.ceil(Scenario.WIND_BANDWIDTH * dt / referenceBandwidthDtProduct).toInt())
        val subDt = dt / substeps
        repeat(substeps) {
            val u0 = nrand()
            val dx0 = x[1]
            val dx1 = -2 * Scenario.WIND_DAMPING * Scenario.WIND_BANDWIDTH * x[1] +
                    Scenario.WIND_BANDWIDTH * Scenario.WIND_BANDWIDTH * (-x[0] + Scenario.WIND_GAIN * u0)
            x[0] += dx0 * subDt
            x[1] += dx1 * subDt
        }
        return x[0]
    }

    private val ny get() = map.ny
    private val nx get() = map.nx
    private val nxy get() = map.nxy

    /** Port of compute_bc: sets the 4 corners from stochastic boundary speed, then linearly
     *  interpolates the rest of each edge. */
    private fun computeBc(c1: Double, c2: Double, speedc: DoubleArray, uArr: DoubleArray, vArr: DoubleArray, mesh: DoubleArray) {
        mesh[0] = c1 * (mesh[ny] + speedc[0] + mesh[1] + speedc[0] - 4 * mesh[0]) -
                c2 * uArr[0] * (mesh[ny] - speedc[0]) - c2 * vArr[0] * (mesh[1] - speedc[0])
        mesh[ny - 1] = c1 * (mesh[2 * ny - 1] + speedc[1] + speedc[1] + mesh[ny - 2] - 4 * mesh[ny - 1]) -
                c2 * uArr[ny - 1] * (mesh[2 * ny - 1] - speedc[1]) - c2 * vArr[ny - 1] * (speedc[1] - mesh[ny - 2])
        mesh[nxy - ny] = c1 * (speedc[2] + mesh[nxy - 2 * ny] + mesh[nxy - ny + 1] + speedc[2] - 4 * mesh[nxy - ny]) -
                c2 * uArr[nxy - ny] * (speedc[2] - mesh[nxy - 2 * ny]) - c2 * vArr[nxy - ny] * (mesh[nxy - ny + 1] - speedc[2])
        mesh[nxy - 1] = c1 * (speedc[3] + mesh[nxy - ny - 1] + speedc[3] + mesh[nxy - 2] - 4 * mesh[nxy - 1]) -
                c2 * uArr[nxy - 1] * (speedc[3] - mesh[nxy - ny - 1]) - c2 * vArr[nxy - 1] * (speedc[3] - mesh[nxy - 2])

        val delTop = (mesh[ny - 1] - mesh[0]) / (ny - 1)
        for (j in 1 until ny - 1) mesh[j] = mesh[0] + delTop * j

        val delBot = (mesh[nxy - 1] - mesh[nxy - ny]) / (ny - 1)
        for (j in 1 until ny - 1) mesh[nxy - ny + j] = mesh[nxy - ny] + delBot * j

        val delLeft = (mesh[nxy - ny] - mesh[0]) / (nx - 1)
        for (i in 1 until nx - 1) mesh[i * ny] = mesh[0] + delLeft * i

        val delRight = (mesh[nxy - 1] - mesh[ny - 1]) / (nx - 1)
        for (i in 1 until nx - 1) mesh[i * ny + ny - 1] = mesh[ny - 1] + delRight * i
    }

    /** Port of compute_obstacle_bc: relaxed non-slip condition near obstacles. */
    private fun computeObstacleBc(field: DoubleArray) {
        System.arraycopy(field, 0, scratch, 0, nxy)
        for (i in 1 until nx - 1) {
            for (j in 1 until ny - 1) {
                if (map.isBlockedRegionSquare(i.toDouble(), j.toDouble(), 1.0)) {
                    val index = i * ny + j
                    scratch[index] = (field[index - 1] + field[index + 1] + field[index - ny] + field[index + ny]) / 4.1
                }
            }
        }
        System.arraycopy(scratch, 0, field, 0, nxy)
    }

    /** Port of compute_mesh_CD: central-difference advection/diffusion update. */
    private fun computeMeshCd(c1: Double, c2: Double, uArr: DoubleArray, vArr: DoubleArray, mesh: DoubleArray) {
        System.arraycopy(mesh, 0, scratch, 0, nxy)
        for (i in 1 until nx - 1) {
            for (j in 1 until ny - 1) {
                val index = i * ny + j
                scratch[index] += (c1 * (mesh[index + ny] + mesh[index - ny] + mesh[index + 1] + mesh[index - 1] - 4 * mesh[index]) -
                        c2 * uArr[index] * (mesh[index + ny] - mesh[index - ny]) - c2 * vArr[index] * (mesh[index + 1] - mesh[index - 1]))
            }
        }
        System.arraycopy(scratch, 0, mesh, 0, nxy)
    }

    /** Port of compute_velocity: advances the wind field u,v by one time step. */
    private fun computeVelocity() {
        val c1 = Scenario.K * Scenario.DEL_T / 2
        val c2 = Scenario.DEL_T / 2

        val speedxc = DoubleArray(4); val speedyc = DoubleArray(4)
        for (i in 0 until 4) {
            speedxc[i] = Scenario.MEAN_U + coloredNoise(xBoundary[i], 1.0)
            speedyc[i] = Scenario.MEAN_V + coloredNoise(yBoundary[i], 1.0)
        }

        computeBc(c1, c2, speedxc, u, v, u)
        computeBc(c1, c2, speedyc, u, v, v)

        computeMeshCd(c1, c2, u, v, u)
        computeMeshCd(c1, c2, u, v, v)

        computeObstacleBc(u)
        computeObstacleBc(v)
    }

    // ##### DENSITY (puffs) #####

    private fun addPuffs() {
        repeat(Scenario.EMIT_RATE) {
            puffs.add(
                Puff(
                    x = Scenario.SOURCE_EX + (1 - 2 * rnd.nextDouble()),
                    y = Scenario.SOURCE_EY + (1 - 2 * rnd.nextDouble()),
                    r = Scenario.R0,
                    t = 0.0
                )
            )
        }
    }

    private fun removePuffs() {
        var p = 0
        while (p < puffs.size) {
            val puff = puffs[p]
            if (puff.x >= nx + puff.r || puff.x < -puff.r || puff.y >= ny + puff.r || puff.y < -puff.r) {
                puffs[p] = puffs[puffs.size - 1]
                puffs.removeAt(puffs.size - 1)
                // do not advance p -- re-check the swapped-in element, matching puffs[p--]=puffs[--NUM_PUFFS]
            } else {
                p++
            }
        }
    }

    private fun movePuffs() {
        addPuffs()
        for (puff in puffs) {
            val x = Math.round(puff.x).toInt(); val y = Math.round(puff.y).toInt()
            if (x in 0 until nx && y in 0 until ny) {
                val index = x * ny + y
                var newX = puff.x + u[index] * (1 + Scenario.SIGMA_NUX * (1 - 2 * rnd.nextDouble())) * Scenario.DEL_T
                var newY = puff.y + v[index] * (1 + Scenario.SIGMA_NUY * (1 - 2 * rnd.nextDouble())) * Scenario.DEL_T

                if (map.isBlockedPoint(newX, newY)) {
                    if (!map.isBlockedPoint(newX, puff.y)) {
                        puff.x = newX
                    } else if (!map.isBlockedPoint(puff.x, newY)) {
                        puff.y = newY
                    } else {
                        val theta = atan2(puff.y - newY, puff.x - newX)
                        val r = hypot(puff.x - newX, puff.y - newY)
                        val thetaOffset = Math.PI / 4
                        val xPlus = newX + r * cos(theta + thetaOffset); val yPlus = newY + r * sin(theta + thetaOffset)
                        val xMinus = newX + r * cos(theta - thetaOffset); val yMinus = newY + r * sin(theta - thetaOffset)
                        if (!map.isBlockedPoint(xPlus, yPlus)) {
                            puff.x = xPlus; puff.y = yPlus
                        } else if (!map.isBlockedPoint(xMinus, yMinus)) {
                            puff.x = xMinus; puff.y = yMinus
                        }
                        // otherwise: give up, keep the puff where it was
                    }
                } else {
                    puff.x = newX
                    puff.y = newY
                }
            } else {
                // outside the world: drift with the mean wind
                puff.x += Scenario.MEAN_U * Scenario.DEL_T
                puff.y += Scenario.MEAN_V * Scenario.DEL_T
            }
            puff.r = kotlin.math.sqrt(Scenario.R0 * Scenario.R0 + Scenario.GAMMA * puff.t)
            puff.t += Scenario.DEL_T
        }
        removePuffs()
    }

    private fun puffRhoImpact(dist: Double, puffR: Double): Double {
        if (dist > puffR) return 0.0
        val coefficient = Scenario.Q / 15.7496
        return coefficient / (puffR * puffR * puffR) * exp(-dist * dist / (puffR * puffR))
    }

    /** Port of compute_density: rasterizes puffs (Gaussian blobs) into the rho grid,
     *  redirecting a puff's contribution around obstacles that occlude it. */
    private fun computeDensity() {
        rho.fill(0.0)
        for (puff in puffs) {
            val x = puff.x; val y = puff.y; val r = puff.r
            val begI = min(nx - 1.0, max(0.0, x - r)).toInt()
            val begJ = min(ny - 1.0, max(0.0, y - r)).toInt()
            val endI = min(nx - 1.0, max(0.0, x + r)).toInt()
            val endJ = min(ny - 1.0, max(0.0, y + r)).toInt()
            for (i in begI..endI) {
                for (j in begJ..endJ) {
                    if (map.isBlockedPoint(i.toDouble(), j.toDouble())) {
                        var hitX = i.toDouble(); var hitY = j.toDouble()
                        if (kotlin.math.abs(x - i) > 1) {
                            val slope = (y - j) / (x - i)
                            val pointSlopeOffset = -slope * x + y
                            val stepDir = if (i < x) 0.5 else -0.5
                            var guard = 0
                            do {
                                hitX += stepDir
                                hitY = slope * hitX + pointSlopeOffset
                                guard++
                            } while (map.isBlockedPoint(hitX, hitY) && guard < 4096)
                        } else {
                            val stepDir = if (j < y) 0.5 else -0.5
                            var guard = 0
                            do {
                                hitY += stepDir
                                guard++
                            } while (map.isBlockedPoint(hitX, hitY) && guard < 4096)
                        }
                        if (map.isWorldPoint(hitX, hitY)) {
                            val impact = puffRhoImpact(hypot(x - hitX, y - hitY), r)
                            if (impact != 0.0) rho[hitX.toInt() * ny + hitY.toInt()] += impact
                        }
                    } else {
                        val impact = puffRhoImpact(hypot(x - i, y - j), r)
                        if (impact != 0.0) rho[i * ny + j] += impact
                    }
                }
            }
        }
    }

    /** Belt-and-suspenders safety net: if anything ever pushes u/v to a non-finite
     *  value (NaN/Infinity) -- the turbulence-instability bug fixed above, or any future
     *  numerical edge case -- reset just that cell to the mean wind rather than letting
     *  garbage propagate into rendering and crash the app. Should be a no-op in normal
     *  operation. */
    private fun sanitize(field: DoubleArray, fallback: Double) {
        for (i in field.indices) {
            if (!field[i].isFinite()) field[i] = fallback
        }
    }

    /** Advance the whole plume simulation by one tick. */
    fun tick() {
        computeVelocity()
        sanitize(u, Scenario.MEAN_U)
        sanitize(v, Scenario.MEAN_V)
        movePuffs()
        computeDensity()
    }
}
