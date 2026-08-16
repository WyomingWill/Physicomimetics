package com.example.combinedsimulations.fluxotaxis.sim

/**
 * Kotlin port of Scenario.h.
 *
 * These are `var`s (not `val`s) because the UI lets the user tune wind,
 * source location, and swarm size at runtime instead of picking one of the
 * four fixed Scenario9_*.h variants at compile time like the original C tool did.
 *
 * Anything MakeMap-only (SIZE, SCALE) was dropped per your request to skip
 * MakeMap.c -- maps come from the MapGen.c-style random generator instead.
 */
object Scenario {
    // ----- PlumeGen.c physics -----
    // Advection/diffusion coefficient (K in compute_mesh_CD's c1 = K*DEL_T/2)
    var K: Double = 0.5

    // Colored-noise boundary wind process (Farrell's Integral.cpp port; see
    // PlumeSimulator.kt's coloredNoise())
    var WIND_DAMPING: Double = 0.09
    var WIND_BANDWIDTH: Double = 0.01
    var WIND_GAIN: Double = 0.77

    // Mean wind vector -- this is what the four Scenario9_*.h files vary
    var MEAN_U: Double = 0.77
    var MEAN_V: Double = 0.05

    var DEL_T: Double = 1.0

    // Puff/plume emitter parameters
    var EMIT_RATE: Int = 1
    var Q: Double = 80.3 / EMIT_RATE
    var R0: Double = 3.50
    var GAMMA: Double = 0.03
    var SIGMA_NUX: Double = 5.0
    var SIGMA_NUY: Double = 5.0

    // Source (emitter) location -- settable by tapping the map in the UI
    var SOURCE_EX: Int = 12
    var SOURCE_EY: Int = 39

    // ----- TraceGen.c agents -----
    const val AGENT_RADIUS: Double = 0.5
    val AGENT_CLEARANCE: Double get() = 1.5 * AGENT_RADIUS
    val SENS_RADIUS: Double get() = 10 * AGENT_RADIUS

    val AP_VMAX: Double get() = 3.0 / 12.0
    val AP_FGOAL: Double get() = AP_VMAX / 2.0

    var ANEMOMETER_THRESHOLD: Double = 0.0005
    var CHEMICAL_SENS_THRESHOLD: Double = 0.0001

    // FLUXO_MODE_* conditional-compilation flags from Scenario.h, now runtime toggles
    var FLUXO_MODE_GOOD_CHEM_SENSOR: Boolean = true
    var FLUXO_MODE_DISTANCE_BIAS: Boolean = false
    var FLUXO_MODE_FALLBACK_ANEMO: Boolean = false
    var FLUXO_MODE_FALLBACK_CHEMO: Boolean = false

    // Live toggle for the boustrophedon casting sweep -- the runtime equivalent of the
    // "WMS Turn Casting off?" edit in TraceGen.c, which hard-coded it off at compile time.
    // Gates both standalone Casting mode and every algorithm's casting fallback (see
    // agentCasto in Fluxotaxis.kt).
    var CASTING_ENABLED: Boolean = true

    // If an agent's casting heading hasn't naturally changed (edge/obstacle-triggered
    // flip) in this many ticks, agentCasto() forces a fresh random direction anyway --
    // fixes agents getting permanently wedged in a corner pocket that's never close
    // enough to any single edge/obstacle to trigger the normal flip conditions.
    var CASTING_STUCK_TIMEOUT_TICKS: Int = 150

    // Emitter proximity clearance check used by PlumeGen's read_map_file()
    const val MIN_EMITTER_CLEARANCE: Double = 2.0
}
