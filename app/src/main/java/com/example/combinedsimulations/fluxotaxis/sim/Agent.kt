package com.example.combinedsimulations.fluxotaxis.sim

/** Port of TraceGen.h's agent_t. `neighbors` mirrors the GPtrArray neighbor cache.
 *
 *  castLongitude/castLatitude default to 1 (not 0) -- this is TraceGen.c's original
 *  value before WMS's "Turn Casting off?" edit set it to 0, disabling the boustrophedon
 *  sweep everywhere it was used (standalone Casting mode, and as anemo/chemo/fluxo's
 *  fallback when they have no signal). Re-enabled at WMS's request. The actual starting
 *  direction assigned to each agent is randomized once per swarm placement (see
 *  SwarmController.placeAgents() in Fluxotaxis.kt), not left at this default. */
class Agent(
    var x: Double,
    var y: Double,
    var u: Double = 0.0,
    var v: Double = 0.0,
    var r: Double = Scenario.SENS_RADIUS,
    var castLongitude: Int = 1,
    var castLatitude: Int = 1,
) {
    // Ticks since castLongitude/castLatitude last actually changed for this agent
    // (whether it triggered the flip itself, near an edge/obstacle, or received it via
    // sync from a neighbor). Used to detect an agent that's stuck somewhere -- a corner
    // pocket, say -- never close enough to any single edge/obstacle to trigger the
    // normal flip conditions. See agentCasto()'s stuck-timeout check.
    var ticksSinceCastFlip: Int = 0

    val neighbors: MutableList<Agent> = mutableListOf()
}

/** Port of TraceGen.h's swarm_t (minus the evaluation-only bookkeeping fields, which
 *  the mobile app doesn't need since there's no batch CSV evaluation mode). */
class Swarm(val agents: MutableList<Agent>) {
    val anemoThreshold: Double = Scenario.ANEMOMETER_THRESHOLD
    val chemoThreshold: Double = Scenario.CHEMICAL_SENS_THRESHOLD

    // Time (in ticks) the swarm first got within SENS_RADIUS of the emitter, shown in
    // the Status panel. -1 means not found yet.
    var bestTime: Int = -1
}

enum class CptAlgorithm(val label: String) {
    FLUXOTAXIS("Fluxotaxis"),
    CHEMOTAXIS("Chemotaxis"),
    ANEMOTAXIS("Anemotaxis"),
    CASTING("Casting"),
}
