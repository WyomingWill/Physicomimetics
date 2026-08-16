package com.example.combinedsimulations.fluxotaxis

import com.example.combinedsimulations.fluxotaxis.sim.CptAlgorithm
import com.example.combinedsimulations.fluxotaxis.sim.MapConfig
import com.example.combinedsimulations.fluxotaxis.sim.MapGenerator
import com.example.combinedsimulations.fluxotaxis.sim.Scenario
import com.example.combinedsimulations.fluxotaxis.sim.SimulationEngine
import com.example.combinedsimulations.fluxotaxis.sim.WorldMap
import kotlin.random.Random

/**
 * Adapter around SimulationEngine that fits this app's "no-arg constructor, then setup()"
 * pattern (see UniformCoverageSimulation, ApoSimulation, etc.), instead of Compose state.
 * FluxotaxisActivity/FluxotaxisSimulationView both hold a reference to one of these,
 * exactly like every other module's Activity+View pair holds its own Simulation object.
 */
class FluxotaxisSimulation {

    enum class PlacementMode { SOURCE, SWARM, DONE }

    // ----- configuration (mirrors what the Compose version's sliders controlled) -----
    var mapDimension = 96
    var numBlocks = 14
    var blockSize = 6
    var mapSeed = 1L

    var numAgents = 12
    var algorithm = CptAlgorithm.FLUXOTAXIS
    var meanU = 0.05; var meanV = 0.77 // "From the North" default -- see WindPreset in the Activity
    var windBandwidth = 0.01
    var castingEnabled = true

    var showVectors = true
    var vectorSpacing = 6
    var matchedVectorScale = true
    var logColorScale = true

    // ----- live state -----
    var previewMap: WorldMap? = null
        private set
    var engine: SimulationEngine? = null
        private set

    var placementMode: PlacementMode = PlacementMode.SOURCE
        private set
    var sourceX = -1
    var sourceY = -1
    var swarmLx = -1.0
    var swarmLy = -1.0

    var statusMessage = "Tap the map to place the plume source, then the swarm start point."
        private set

    /** Lets the Activity surface something like an uncaught exception onto the same
     *  status line everything else uses (rendered on-canvas at the bottom of the map),
     *  rather than needing a separate UI element just for error text. */
    fun reportError(message: String) {
        statusMessage = message
    }

    /** The map currently relevant for drawing/tapping: the live engine's once running,
     *  else the pre-run preview. */
    val displayMap: WorldMap? get() = engine?.map ?: previewMap

    fun regenerateMap() {
        engine = null
        sourceX = -1; sourceY = -1; swarmLx = -1.0; swarmLy = -1.0
        placementMode = PlacementMode.SOURCE
        previewMap = MapGenerator.generate(mapSeed, mapDimension, numBlocks, blockSize)
        statusMessage = "Tap the map to place the plume source, then the swarm start point."
    }

    /** Randomizes the seed first, then regenerates -- a genuinely different building
     *  layout. Distinct from regenerateMap(), which restarts placement on the SAME map
     *  (same seed) so you can compare algorithms/wind/placement fairly on one layout. */
    fun generateNewMap() {
        mapSeed = Random.nextLong()
        regenerateMap()
    }

    /** Call from the View's touch handler with a grid cell. Returns true if this tap
     *  completed placement and started the run. */
    fun onMapTap(gridX: Int, gridY: Int): Boolean {
        val map = displayMap ?: return false
        when (placementMode) {
            PlacementMode.SOURCE -> {
                if (!MapGenerator.isValidEmitterLocation(map, gridX, gridY)) {
                    statusMessage = "Too close to an obstacle or the map edge -- pick another spot for the source."
                    return false
                }
                sourceX = gridX; sourceY = gridY
                placementMode = PlacementMode.SWARM
                statusMessage = "Source set. Now tap where the agent swarm should start."
            }
            PlacementMode.SWARM -> {
                if (map.isBlockedRegionSquare(gridX.toDouble(), gridY.toDouble(), Scenario.SENS_RADIUS)) {
                    statusMessage = "Too close to obstacles for the whole swarm to fit -- pick another spot."
                    return false
                }
                swarmLx = gridX.toDouble(); swarmLy = gridY.toDouble()
                placementMode = PlacementMode.DONE
                startSimulation()
                return true
            }
            PlacementMode.DONE -> { /* ignore taps until New Scenario / Regenerate */ }
        }
        return false
    }

    fun startSimulation() {
        if (sourceX < 0 || swarmLx < 0) {
            statusMessage = "Place the source and swarm start point first."
            return
        }
        val cfg = MapConfig(mapSeed, mapDimension, numBlocks, blockSize)
        Scenario.MEAN_U = meanU
        Scenario.MEAN_V = meanV
        Scenario.WIND_BANDWIDTH = windBandwidth
        Scenario.CASTING_ENABLED = castingEnabled
        engine = SimulationEngine(
            mapConfig = cfg,
            sourceX = sourceX,
            sourceY = sourceY,
            swarmLx = swarmLx,
            swarmLy = swarmLy,
            numAgents = numAgents,
            algorithm = algorithm,
            plumeSeed = Random.nextLong(),
        )
        statusMessage = "Running ${algorithm.label} with $numAgents agents."
    }

    fun resetSwarmOnly() {
        val e = engine ?: return
        e.resetSwarm(numAgents, swarmLx, swarmLy)
        statusMessage = "Swarm reset. Ready to run."
    }

    /** Advance one tick. Returns true if the emitter was found on THIS call (the
     *  transition), so the caller can show a one-time message without spamming it. */
    fun step(): Boolean {
        val e = engine ?: return false
        val wasFound = e.emitterFound
        e.tick()
        val justFound = e.emitterFound && !wasFound
        if (justFound) {
            statusMessage = "Emitter located after ${e.tickCount} steps! Still running."
        }
        return justFound
    }
}
