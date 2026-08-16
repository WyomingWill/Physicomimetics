package com.example.combinedsimulations.fluxotaxis.sim

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Direct port of the swarm-control half of TraceGen.c: artificial-physics formation
 * keeping, obstacle/collision avoidance, and the anemotaxis/casting/chemotaxis/
 * fluxotaxis steering behaviors. Function names mirror the C originals in comments
 * so you can diff this against TraceGen.c.
 */
class SwarmController(private val map: WorldMap, private val plume: PlumeSimulator) {

    /** Port of place_agents(): rings agents out from (lx,ly), skipping obstacles/overlaps.
     *  Casting direction is randomized once per placement (one direction for the whole
     *  swarm, not per-agent -- a per-agent random direction would have the swarm
     *  fragment and move apart from tick 1, since sync only kicks in once an edge/
     *  obstacle flip actually triggers). Applies to both standalone Casting mode and
     *  every algorithm's casting fallback. */
    fun placeAgents(numAgents: Int, lx: Double, ly: Double): Swarm {
        val placed = ArrayList<Agent>(numAgents)
        val initialLongitude = if (kotlin.random.Random.nextBoolean()) 1 else -1
        val initialLatitude = if (kotlin.random.Random.nextBoolean()) 1 else -1
        var r = 0.01
        while (r < 2 * map.nx && placed.size < numAgents) {
            var theta = r
            val thetaEnd = r + 2 * Math.PI
            while (theta < thetaEnd && placed.size < numAgents) {
                val i = lx + r * cos(theta)
                val j = ly + r * sin(theta)
                if (map.isWorldRegionSquare(i, j, Scenario.AGENT_CLEARANCE) &&
                    !map.isBlockedRegionSquare(i, j, Scenario.AGENT_CLEARANCE)
                ) {
                    val conflict = placed.any { hypot(it.x - i, it.y - j) < Scenario.AGENT_CLEARANCE }
                    if (!conflict) {
                        placed.add(
                            Agent(
                                x = i, y = j, u = 0.0, v = 0.0,
                                r = max(Scenario.AGENT_CLEARANCE, min(Scenario.SENS_RADIUS, 0.5 * Scenario.SENS_RADIUS)),
                                castLongitude = initialLongitude,
                                castLatitude = initialLatitude,
                            )
                        )
                    }
                }
                theta += Scenario.AGENT_RADIUS / r
            }
            r += Scenario.AGENT_RADIUS
        }
        check(placed.size == numAgents) { "could not place all $numAgents agents (map too small/crowded)" }
        return Swarm(placed)
    }

    /** Port of locate_neighbors(): O(n^2) pairwise neighbor cache within SENS_RADIUS. */
    fun locateNeighbors(swarm: Swarm) {
        val agents = swarm.agents
        for (a in agents) a.neighbors.clear()
        for (a in agents.indices) {
            val agent = agents[a]
            for (o in a + 1 until agents.size) {
                val other = agents[o]
                if (hypot(other.x - agent.x, other.y - agent.y) <= Scenario.SENS_RADIUS) {
                    agent.neighbors.add(other)
                    other.neighbors.add(agent)
                }
            }
        }
    }

    private fun readSensor(data: DoubleArray, agent: Agent): Double {
        val ax = min(map.nx - 1, max(0, agent.x.roundToInt()))
        val ay = min(map.ny - 1, max(0, agent.y.roundToInt()))
        return data[ax * map.ny + ay]
    }

    private fun limitSpeed(agent: Agent) {
        if (hypot(agent.u, agent.v) > Scenario.AP_VMAX) {
            val bearing = atan2(agent.v, agent.u)
            agent.u = Scenario.AP_VMAX * cos(bearing)
            agent.v = Scenario.AP_VMAX * sin(bearing)
        }
    }

    private fun syncCastLongitude(newLongitude: Int, agent: Agent, visited: MutableSet<Agent> = mutableSetOf()) {
        if (agent.castLongitude != newLongitude && visited.add(agent)) {
            agent.castLongitude = newLongitude
            agent.ticksSinceCastFlip = 0
            for (n in agent.neighbors) syncCastLongitude(newLongitude, n, visited)
        }
    }

    private fun syncCastLatitude(newLatitude: Int, agent: Agent, visited: MutableSet<Agent> = mutableSetOf()) {
        if (agent.castLatitude != newLatitude && visited.add(agent)) {
            agent.castLatitude = newLatitude
            agent.ticksSinceCastFlip = 0
            for (n in agent.neighbors) syncCastLatitude(newLatitude, n, visited)
        }
    }

    // ----- AP (artificial physics / physicomimetics formation control) -----

    /** Port of ap_compute_new_velocity(): Lennard-Jones-like formation force from one neighbor. */
    private fun apComputeNewVelocity(other: Agent, agent: Agent) {
        val delX = other.x - agent.x; val delY = other.y - agent.y
        val rActual = max(2 * Scenario.AGENT_RADIUS, hypot(delX, delY))
        val rDesired = (other.r + agent.r) / 2
        if (rActual <= Scenario.SENS_RADIUS) {
            val theta = atan2(delY, delX)
            val f = rDesired.pow(1.0) / rActual.pow(2.1) - rDesired.pow(1.7) / rActual.pow(2.7)
            agent.u += f * cos(theta)
            agent.v += f * sin(theta)
        }
    }

    /** Port of ap_maintain_formation(). */
    fun apMaintainFormation(swarm: Swarm) {
        for (agent in swarm.agents) {
            for (neighbor in agent.neighbors) apComputeNewVelocity(neighbor, agent)
        }
    }

    /** Port of avoid_obstacles_reflection(): try flipping v, then u, then both; else repel
     *  from nearby obstacle mass; else nearly stop. */
    fun avoidObstaclesReflection(swarm: Swarm) {
        for (agent in swarm.agents) {
            limitSpeed(agent)

            val oldX = agent.x; val oldY = agent.y
            var newX = oldX + agent.u; var newY = oldY + agent.v
            if (map.isBlockedRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE) || !map.isWorldRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE)) {
                newX = oldX + agent.u; newY = oldY - agent.v
                if (!map.isBlockedRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE) && map.isWorldRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE)) {
                    agent.v *= -1
                } else {
                    newX = oldX - agent.u; newY = oldY + agent.v
                    if (!map.isBlockedRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE) && map.isWorldRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE)) {
                        agent.u *= -1
                    } else {
                        newX = oldX - agent.u; newY = oldY - agent.v
                        if (!map.isBlockedRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE) && map.isWorldRegionSquare(newX, newY, Scenario.AGENT_CLEARANCE)) {
                            agent.u *= -1; agent.v *= -1
                        } else {
                            // Repel from both nearby obstacle mass AND the map edge itself.
                            // The original (and this port, until now) only accounted for
                            // isBlockedPoint() here -- an agent cornered against open boundary
                            // with no buildings nearby found nothing to repel from at all,
                            // defaulting to a meaningless fixed push (atan2(0,0)=0, i.e. always
                            // +X) that does nothing to solve a Y-boundary problem. This was a
                            // latent bug in the algorithm itself, just never exercised while
                            // casting (the main driver that pushes agents toward edges on
                            // purpose) was disabled.
                            var newU = 0.0; var newV = 0.0
                            var x = floor(oldX - Scenario.SENS_RADIUS)
                            while (x <= ceil(oldX + Scenario.SENS_RADIUS)) {
                                var y = floor(oldY - Scenario.SENS_RADIUS)
                                while (y <= ceil(oldY + Scenario.SENS_RADIUS)) {
                                    val distance = max(0.1, hypot(x - oldX, y - oldY))
                                    val repulsive = distance <= Scenario.SENS_RADIUS &&
                                        (map.isBlockedPoint(x, y) || !map.isWorldPoint(x, y))
                                    if (repulsive) {
                                        val theta = atan2(y - oldY, x - oldX)
                                        newU -= 1 / distance * cos(theta)
                                        newV -= 1 / distance * sin(theta)
                                    }
                                    y += 1.0
                                }
                                x += 1.0
                            }
                            val theta = atan2(newV, newU)
                            agent.u = Scenario.AP_VMAX * cos(theta)
                            agent.v = Scenario.AP_VMAX * sin(theta)

                            val checkX = agent.x + agent.u; val checkY = agent.y + agent.v
                            if (map.isBlockedRegionSquare(checkX, checkY, Scenario.AGENT_CLEARANCE) || !map.isWorldRegionSquare(checkX, checkY, Scenario.AGENT_CLEARANCE)) {
                                agent.u *= -0.0001; agent.v *= -0.0001
                            }
                        }
                    }
                }
            }
        }
    }

    private val slowdownFactor = 0.7
    private val minAgentVel = Scenario.AP_VMAX / 100.0

    /** Port of avoid_agent_collision(): successively brake both agents until predicted
     *  positions clear AGENT_CLEARANCE. */
    private fun avoidAgentCollision(other: Agent, agent: Agent): Boolean {
        var collision = false
        var conflict: Boolean
        val maxSpeed = max(hypot(agent.u, agent.v), hypot(other.u, other.v)).coerceAtLeast(1e-9)
        var iter = 1 + ceil(ln(minAgentVel / maxSpeed) / ln(slowdownFactor)).toInt()
        do {
            val ax = agent.x + agent.u; val ay = agent.y + agent.v
            val ox = other.x + other.u; val oy = other.y + other.v
            conflict = hypot(ox - ax, oy - ay) < Scenario.AGENT_CLEARANCE
            if (conflict) {
                collision = true
                agent.u *= slowdownFactor; agent.v *= slowdownFactor
                other.u *= slowdownFactor; other.v *= slowdownFactor
            }
            iter--
        } while (conflict && iter > 0)
        return collision
    }

    /** Port of move_agents_realistically(): avoidance passes, then integrate x += u, y += v. */
    fun moveAgentsRealistically(swarm: Swarm) {
        avoidObstaclesReflection(swarm)

        var collision: Boolean
        var iter = swarm.agents.size.coerceAtLeast(1)
        do {
            collision = false
            for (agent in swarm.agents) {
                for (neighbor in agent.neighbors) {
                    val c = avoidAgentCollision(neighbor, agent)
                    if (c) collision = true
                }
            }
            iter--
        } while (collision && iter > 0)

        for (agent in swarm.agents) {
            agent.x += agent.u
            agent.y += agent.v

            // Defense in depth: everything above should already keep agents on the map
            // (agentCasto's SENS_RADIUS pre-turn, avoidObstaclesReflection's world-bound
            // checks, the edge-repulsion fix above), but clamp as a last resort so a
            // degenerate case can never visibly push an agent off-canvas.
            agent.x = agent.x.coerceIn(0.0, (map.nx - 1).toDouble())
            agent.y = agent.y.coerceIn(0.0, (map.ny - 1).toDouble())
        }
        locateNeighbors(swarm)
        reconcileCastDirections(swarm)
    }

    /**
     * After the neighbor graph updates each tick, two previously-separate groups of
     * agents can find themselves connected again with mismatched casting headings --
     * each group may have independently flipped, or been forced into a fresh random
     * direction by the stuck-timeout, while apart. syncCastLongitude/syncCastLatitude
     * only propagate a heading when a *new* flip event fires; simply becoming neighbors
     * again doesn't trigger anything on its own, so without this the two groups would
     * just sit there sweeping in different directions instead of moving as one formation.
     *
     * For every connected component of the current neighbor graph, pick one direction
     * per axis (majority vote among the component's members, ties broken toward +1) and
     * assign it directly to every mismatched member. Deliberately does NOT go through
     * syncCastLongitude/syncCastLatitude here: those functions only start propagating
     * from an agent whose own field differs from the target value, and the component's
     * arbitrary first-discovered member might already hold the majority value -- in
     * which case that entry check fails immediately and the whole flood-fill silently
     * never starts, leaving minority agents elsewhere in the component un-reconciled.
     * Since the whole component is already enumerated here, just assign directly.
     */
    private fun reconcileCastDirections(swarm: Swarm) {
        val visited = mutableSetOf<Agent>()
        for (start in swarm.agents) {
            if (!visited.add(start)) continue

            val component = mutableListOf(start)
            val stack = ArrayDeque<Agent>()
            stack.add(start)
            while (stack.isNotEmpty()) {
                val a = stack.removeLast()
                for (n in a.neighbors) {
                    if (visited.add(n)) {
                        component.add(n)
                        stack.add(n)
                    }
                }
            }
            if (component.size <= 1) continue

            if (component.any { it.castLongitude != component[0].castLongitude }) {
                val chosen = if (component.count { it.castLongitude > 0 } * 2 >= component.size) 1 else -1
                for (a in component) {
                    if (a.castLongitude != chosen) {
                        a.castLongitude = chosen
                        a.ticksSinceCastFlip = 0
                    }
                }
            }
            if (component.any { it.castLatitude != component[0].castLatitude }) {
                val chosen = if (component.count { it.castLatitude > 0 } * 2 >= component.size) 1 else -1
                for (a in component) {
                    if (a.castLatitude != chosen) {
                        a.castLatitude = chosen
                        a.ticksSinceCastFlip = 0
                    }
                }
            }
        }
    }

    // ----- CPT steering behaviors -----

    /** Port of agent_anemo(): fly upwind if wind+chem both above threshold, else cast. */
    fun agentAnemo(swarm: Swarm, agent: Agent) {
        val uu = readSensor(plume.u, agent); val vv = readSensor(plume.v, agent)
        if (readSensor(plume.rho, agent) >= swarm.chemoThreshold && hypot(uu, vv) >= swarm.anemoThreshold) {
            val theta = atan2(vv, uu)
            agent.u -= Scenario.AP_FGOAL * cos(theta)
            agent.v -= Scenario.AP_FGOAL * sin(theta)
        } else {
            agentCasto(swarm, agent)
        }
    }

    /** Port of agent_casto(): boustrophedon casting pattern, synced across neighbors.
     *  Re-enabled per WMS's request: castLongitude/castLatitude seed at 1 (Agent.kt),
     *  so this actually sweeps now, instead of the 0*-1=0 no-op it was left as in
     *  TraceGen.c after the "WMS Turn Casting off?" edit. Standalone Casting mode uses
     *  it directly; agent_fluxo/agent_anemo/agent_chemo all fall back to it when they
     *  have no signal to steer by.
     *
     *  Scenario.CASTING_ENABLED is a live UI toggle for the same on/off behavior WMS's
     *  original edit hard-coded at compile time. Heading-tracking (the flip checks and
     *  neighbor sync below) always keeps running even while disabled, so the heading
     *  doesn't go stale -- only the final force application is gated -- meaning you can
     *  flip the toggle mid-run without needing to reset the swarm.
     *
     *  Stuck-timeout addition: the flip conditions below only trigger from edge/obstacle
     *  proximity, so an agent wedged in a corner pocket that's never quite close enough
     *  to any single edge/obstacle to trip either check could sit there indefinitely.
     *  If this agent's heading hasn't actually changed in CASTING_STUCK_TIMEOUT_TICKS,
     *  force a fresh random direction anyway. */
    fun agentCasto(swarm: Swarm, agent: Agent) {
        val ax = agent.x; val ay = agent.y
        var longitude = agent.castLongitude; var latitude = agent.castLatitude
        var flippedThisAgent = false

        if ((ax < Scenario.SENS_RADIUS && longitude < 0) || (ax >= map.nx - Scenario.SENS_RADIUS && longitude > 0) ||
            (map.isBlockedRegionRectangle(floor(ax - Scenario.AGENT_RADIUS), ay - Scenario.AGENT_RADIUS, ax, ay + Scenario.AGENT_RADIUS) && longitude < 0) ||
            (map.isBlockedRegionRectangle(ax, ay - Scenario.AGENT_RADIUS, ceil(ax + Scenario.AGENT_RADIUS), ay + Scenario.AGENT_RADIUS) && longitude > 0)
        ) {
            longitude *= -1
            syncCastLongitude(longitude, agent)
            flippedThisAgent = true
        }
        if ((ay < Scenario.SENS_RADIUS && latitude < 0) || (ay >= map.ny - Scenario.SENS_RADIUS && latitude > 0) ||
            (map.isBlockedRegionRectangle(ax - Scenario.AGENT_RADIUS, floor(ay - Scenario.AGENT_RADIUS), ax + Scenario.AGENT_RADIUS, ay) && latitude < 0) ||
            (map.isBlockedRegionRectangle(ax - Scenario.AGENT_RADIUS, ay, ax + Scenario.AGENT_RADIUS, ceil(ay + Scenario.AGENT_RADIUS)) && latitude > 0)
        ) {
            latitude *= -1
            syncCastLatitude(latitude, agent)
            flippedThisAgent = true
        }

        if (!flippedThisAgent) {
            agent.ticksSinceCastFlip++
            if (agent.ticksSinceCastFlip >= Scenario.CASTING_STUCK_TIMEOUT_TICKS) {
                longitude = if (kotlin.random.Random.nextBoolean()) 1 else -1
                latitude = if (kotlin.random.Random.nextBoolean()) 1 else -1
                syncCastLongitude(longitude, agent)
                syncCastLatitude(latitude, agent)
                // Reset unconditionally: the random redraw above 50/50 might happen to
                // match the current heading per axis, in which case the sync calls
                // no-op and won't reset this on their own -- but we still just attempted
                // an escape maneuver, so the cooldown should restart regardless.
                agent.ticksSinceCastFlip = 0
            }
        }

        agent.castLongitude = longitude; agent.castLatitude = latitude

        if (Scenario.CASTING_ENABLED) {
            agent.u += longitude * Scenario.AP_FGOAL
            agent.v += latitude * Scenario.AP_FGOAL
        }
    }

    /** Port of agent_chemo(): steer toward the neighbor (or self) with highest concentration. */
    fun agentChemo(swarm: Swarm, agent: Agent) {
        if (agent.neighbors.isEmpty()) { agentCasto(swarm, agent); return }

        var rho = readSensor(plume.rho, agent)
        if (rho < swarm.chemoThreshold) rho = 0.0

        var agentMax = agent.neighbors[0]
        var rhoMax = readSensor(plume.rho, agentMax)
        if (rhoMax < swarm.chemoThreshold) rhoMax = 0.0

        for (n in 1 until agent.neighbors.size) {
            val neighbor = agent.neighbors[n]
            var rhoN = readSensor(plume.rho, neighbor)
            if (rhoN < swarm.chemoThreshold) rhoN = 0.0
            if (rhoN >= swarm.chemoThreshold && rhoN > rhoMax) { rhoMax = rhoN; agentMax = neighbor }
        }

        val force = Math.signum(rhoMax - rho) * Scenario.AP_FGOAL
        if (force != 0.0) {
            val theta = atan2(agentMax.y - agent.y, agentMax.x - agent.x)
            agent.u += force * cos(theta)
            agent.v += force * sin(theta)
        } else {
            agentCasto(swarm, agent)
        }
    }

    /** Port of compute_agent_flux(): the asymmetric flux 'agent' perceives at 'other's location. */
    fun computeAgentFlux(anemoThreshold: Double, chemoThreshold: Double, other: Agent, agent: Agent): Double {
        val rho = readSensor(plume.rho, other)
        val uu = readSensor(plume.u, other); val vv = readSensor(plume.v, other)
        val vel = hypot(uu, vv)
        if (rho < chemoThreshold) return 0.0
        if (vel < anemoThreshold) return 0.0

        val dx = other.x - agent.x; val dy = other.y - agent.y
        var compositeFlux = vel * cos(atan2(vv, uu) - atan2(dy, dx))

        if (Scenario.FLUXO_MODE_GOOD_CHEM_SENSOR) compositeFlux *= rho
        if (Scenario.FLUXO_MODE_DISTANCE_BIAS) compositeFlux *= hypot(dx, dy)

        return compositeFlux
    }

    /** Port of agent_fluxo(): the fluxotaxis behavior -- steer toward whichever neighbor
     *  shows the strongest inflow (negative flux) or outflow (positive flux) of chemical mass.
     *
     *  Fallback chain, at WMS's request: when fluxotaxis has no usable signal (too few
     *  neighbors to compare flux against, or no clear in/outflux among them), it now falls
     *  back to casting directly rather than doing nothing. FLUXO_MODE_FALLBACK_ANEMO/CHEMO
     *  still work exactly as in the original -- if either is turned on, that intermediate
     *  behavior is tried first (and agent_chemo has its own casting fallback baked in, so
     *  the chain still ends at casting either way) -- but casting is now the *terminal*
     *  fallback instead of silence, which is what the original left it as. */
    fun agentFluxo(swarm: Swarm, agent: Agent) {
        val numNeighbors = agent.neighbors.size
        if (numNeighbors > 1) {
            var agentMaxOutflux = agent.neighbors[0]
            var agentMaxInflux = agentMaxOutflux
            var maxOutflux = computeAgentFlux(swarm.anemoThreshold, swarm.chemoThreshold, agentMaxOutflux, agent)
            var maxInflux = maxOutflux

            for (n in 1 until numNeighbors) {
                val neighbor = agent.neighbors[n]
                val nflux = computeAgentFlux(swarm.anemoThreshold, swarm.chemoThreshold, neighbor, agent)
                if (nflux > maxOutflux) { maxOutflux = nflux; agentMaxOutflux = neighbor }
                else if (nflux < maxInflux) { maxInflux = nflux; agentMaxInflux = neighbor }
            }

            if (maxInflux < 0) {
                val theta = atan2(agentMaxInflux.y - agent.y, agentMaxInflux.x - agent.x)
                agent.u += Scenario.AP_FGOAL * cos(theta)
                agent.v += Scenario.AP_FGOAL * sin(theta)
            } else if (maxOutflux > 0) {
                val theta = atan2(agentMaxOutflux.y - agent.y, agentMaxOutflux.x - agent.x)
                agent.u += Scenario.AP_FGOAL * cos(theta)
                agent.v += Scenario.AP_FGOAL * sin(theta)
            } else if (Scenario.FLUXO_MODE_FALLBACK_CHEMO) {
                agentChemo(swarm, agent)
            } else {
                agentCasto(swarm, agent)
            }
        } else if (Scenario.FLUXO_MODE_FALLBACK_ANEMO) {
            agentAnemo(swarm, agent)
        } else {
            agentCasto(swarm, agent)
        }
    }

    // ----- CPT algorithm drivers (port of cpt_anemo/cpt_casto/cpt_chemo/cpt_fluxo/cpt_stayo) -----

    fun step(swarm: Swarm, algorithm: CptAlgorithm) {
        apMaintainFormation(swarm)
        for (agent in swarm.agents) {
            when (algorithm) {
                CptAlgorithm.FLUXOTAXIS -> agentFluxo(swarm, agent)
                CptAlgorithm.CHEMOTAXIS -> agentChemo(swarm, agent)
                CptAlgorithm.ANEMOTAXIS -> agentAnemo(swarm, agent)
                CptAlgorithm.CASTING -> agentCasto(swarm, agent)
            }
        }
        moveAgentsRealistically(swarm)
    }
}
