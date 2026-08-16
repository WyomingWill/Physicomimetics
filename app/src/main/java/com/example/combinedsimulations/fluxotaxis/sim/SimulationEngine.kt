package com.example.combinedsimulations.fluxotaxis.sim

import kotlin.math.hypot

data class MapConfig(
    val seed: Long = 1L,
    val dimension: Int = 96,
    val numBlocks: Int = 14,
    val blockSize: Int = 6,
)

/** Orchestrates one run: generated map -> live plume field -> agent swarm running a CPT algorithm. */
class SimulationEngine(
    val mapConfig: MapConfig,
    sourceX: Int,
    sourceY: Int,
    swarmLx: Double,
    swarmLy: Double,
    numAgents: Int,
    var algorithm: CptAlgorithm,
    plumeSeed: Long = 2L,
) {
    val map: WorldMap = MapGenerator.generate(mapConfig.seed, mapConfig.dimension, mapConfig.numBlocks, mapConfig.blockSize)
    val plume: PlumeSimulator
    private val controller: SwarmController

    var swarm: Swarm
        private set

    var tickCount: Int = 0
        private set

    var emitterFound: Boolean = false
        private set

    init {
        Scenario.SOURCE_EX = sourceX
        Scenario.SOURCE_EY = sourceY
        plume = PlumeSimulator(map, plumeSeed)
        controller = SwarmController(map, plume)
        swarm = controller.placeAgents(numAgents, swarmLx, swarmLy)
        controller.locateNeighbors(swarm)
    }

    fun resetSwarm(numAgents: Int, lx: Double, ly: Double) {
        swarm = controller.placeAgents(numAgents, lx, ly)
        controller.locateNeighbors(swarm)
        tickCount = 0
        emitterFound = false
    }

    /** Advance plume physics and one swarm control step. Returns true once an agent
     *  reaches the emitter within SENS_RADIUS (mirrors TraceGen's generate_cpt_file loop). */
    fun tick(): Boolean {
        plume.tick()
        controller.step(swarm, algorithm)
        tickCount++

        if (!emitterFound) {
            emitterFound = swarm.agents.any {
                hypot(Scenario.SOURCE_EX - it.x, Scenario.SOURCE_EY - it.y) <= Scenario.SENS_RADIUS
            }
            if (emitterFound) swarm.bestTime = tickCount
        }
        return emitterFound
    }
}
