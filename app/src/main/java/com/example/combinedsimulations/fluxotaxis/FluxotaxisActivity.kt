package com.example.combinedsimulations.fluxotaxis

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.combinedsimulations.R
import com.example.combinedsimulations.fluxotaxis.sim.CptAlgorithm
import com.example.combinedsimulations.fluxotaxis.sim.Scenario
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*

class FluxotaxisActivity : AppCompatActivity() {
    private lateinit var simulationView: FluxotaxisSimulationView
    private lateinit var simulation: FluxotaxisSimulation
    private var simulationJob: Job? = null
    private var speedStepsPerSecond = 15

    /**
     * Wind direction presets. Values here are NOT the literal MEAN_U/MEAN_V from the
     * original Scenario9_*.h files -- those were calibrated for CFD_VISgl.c's renderer,
     * which deliberately transposes screen axes (screen-X = column/V, screen-Y = row/U;
     * see the "note the reversal of X and Y" comments throughout that file). This view's
     * renderer does not transpose (screen-X = agent.x/U, screen-Y = agent.y/V, Y growing
     * downward), so using the original's literal numbers would put the compass direction
     * on the wrong axis. Same physical magnitudes (0.77 dominant / 0.05 turbulence-anchor
     * minor), reassigned to the correct axis/sign for this renderer:
     *   North blows toward South (+Y, down)   -> V dominant, positive
     *   South blows toward North (-Y, up)     -> V dominant, negative
     *   East  blows toward West  (-X, left)   -> U dominant, negative
     *   West  blows toward East  (+X, right)  -> U dominant, positive
     */
    private data class WindPreset(val label: String, val meanU: Double, val meanV: Double)
    private val windPresets = listOf(
        WindPreset("From the North", 0.05, 0.77),
        WindPreset("From the East", -0.77, 0.05),
        WindPreset("From the West", 0.77, 0.05),
        WindPreset("From the South", 0.05, -0.77),
    )

    private val infoText = """
        WHAT IS IT?

        A swarm of simple robots searches for the source of a windborne chemical plume drifting through a field of rectangular buildings -- the classic "chemical plume tracing" (CPT) problem. The plume itself is simulated from real fluid dynamics: a turbulent wind field advects and disperses a stream of Gaussian "puffs" released from the source, the same way a scent trail actually spreads outdoors. Fluxotaxis is the primary search strategy: instead of just following the local concentration gradient, each robot compares readings between itself and its neighbors to estimate the direction of mass flux -- literally which way the chemical is flowing -- and steers along that flow back toward its source.

        HOW IT WORKS

        Every simulated agent keeps its neighbors within sensor range, using the physicomimetics lattice force from the other formation models here to hold a loose grid spacing as it moves. Fluxotaxis compares each pair of neighboring readings (wind speed and direction, chemical concentration) to compute an asymmetric in/out flux, then steers toward whichever neighbor shows the strongest inflow or outflow signal. Chemotaxis and Anemotaxis are simpler alternatives (follow the concentration gradient directly, or fly upwind whenever both wind and concentration clear their sensor thresholds); Casting is a boustrophedon sweep -- straight lines that flip direction at a wall or the map edge, spreading the whole connected swarm's turn through the group at once -- used standalone or as every other strategy's fallback when it has no usable signal.

        HOW TO USE IT

        Tap the map to place the plume's emitter, then tap again to place the swarm's starting point -- the run starts automatically on that second tap. Dimension/Obstacles/Obstacle size control the randomly generated building layout (buildings may overlap freely, but non-overlapping ones are always spaced wide enough for two robots to pass through side by side). The wind direction spinner and Turbulence speed slider control the plume's mean wind and how fast its turbulence evolves (1 matches the original's own turbulence rate; higher values make it visibly swing within seconds instead of tens of seconds). Show wind vectors overlays the live wind field as arrows; 1:1 keeps their length/angle proportional between axes rather than exaggerating whichever axis is naturally smaller. Log color scale compresses the plume's density range so its fainter edges stay visible instead of only the hottest core showing color. Pick a search algorithm from the spinner, toggle Casting to enable/disable the sweep behavior everywhere it's used, and use Reset Swarm or New Scenario to start over without leaving the screen.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fluxotaxis)

        simulationView = findViewById(R.id.simulationView)
        simulation = FluxotaxisSimulation()
        simulationView.setSimulation(simulation)
        simulation.regenerateMap()
        simulationView.invalidate()

        setupTabs()
        setupTouch()
        setupControls()
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val interfaceContainer = findViewById<View>(R.id.interfaceContainer)
        val infoContainer = findViewById<View>(R.id.infoContainer)
        findViewById<android.widget.TextView>(R.id.infoText).text = infoText

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    interfaceContainer.visibility = View.VISIBLE
                    infoContainer.visibility = View.GONE
                } else {
                    interfaceContainer.visibility = View.GONE
                    infoContainer.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupTouch() {
        simulationView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val cell = simulationView.screenToGrid(event.x, event.y)
                if (cell != null) {
                    val (gx, gy) = cell
                    val started = simulation.onMapTap(gx, gy)
                    v.invalidate()
                    if (started) startSimulation()
                }
                true
            } else false
        }
    }

    private fun setupControls() {
        findViewById<Slider>(R.id.sliderDimension).addOnChangeListener { _, value, _ ->
            simulation.mapDimension = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelDimension).text = "Dimension: " + value.toInt()
        }
        findViewById<Slider>(R.id.sliderObstacles).addOnChangeListener { _, value, _ ->
            simulation.numBlocks = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelObstacles).text = "Obstacles: " + value.toInt()
        }
        findViewById<Slider>(R.id.sliderObstacleSize).addOnChangeListener { _, value, _ ->
            simulation.blockSize = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelObstacleSize).text = "Obstacle size (area basis): " + value.toInt()
        }
        findViewById<Slider>(R.id.sliderTurbulence).addOnChangeListener { _, value, _ ->
            simulation.windBandwidth = 0.01 * value
            findViewById<android.widget.TextView>(R.id.labelTurbulence).text = "Turbulence speed (orig.=1): " + value.toInt()
        }
        findViewById<Slider>(R.id.sliderVectorSpacing).addOnChangeListener { _, value, _ ->
            simulation.vectorSpacing = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelVectorSpacing).text = "Vector spacing: " + value.toInt()
        }
        findViewById<Slider>(R.id.sliderAgents).addOnChangeListener { _, value, _ ->
            simulation.numAgents = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelAgents).text = "Agents: " + value.toInt()
        }
        findViewById<Slider>(R.id.sliderSpeed).addOnChangeListener { _, value, _ ->
            speedStepsPerSecond = value.toInt().coerceIn(1, 60)
            findViewById<android.widget.TextView>(R.id.labelSpeed).text = "Speed (steps/sec): " + value.toInt()
        }

        findViewById<SwitchMaterial>(R.id.switchVectors).setOnCheckedChangeListener { _, checked ->
            simulation.showVectors = checked
            findViewById<View>(R.id.vectorOptionsGroup).visibility = if (checked) View.VISIBLE else View.GONE
            simulationView.invalidate()
        }
        findViewById<SwitchMaterial>(R.id.switchMatchedScale).setOnCheckedChangeListener { _, checked ->
            simulation.matchedVectorScale = checked; simulationView.invalidate()
        }
        findViewById<SwitchMaterial>(R.id.switchLogScale).setOnCheckedChangeListener { _, checked ->
            simulation.logColorScale = checked; simulationView.invalidate()
        }
        findViewById<SwitchMaterial>(R.id.switchCasting).setOnCheckedChangeListener { _, checked ->
            simulation.castingEnabled = checked
            Scenario.CASTING_ENABLED = checked // live, no reset needed
        }

        val windSpinner = findViewById<Spinner>(R.id.spinnerWindDirection)
        windSpinner.adapter = ArrayAdapter(this, R.layout.fluxo_spinner_item, windPresets.map { it.label })
            .apply { setDropDownViewResource(R.layout.fluxo_spinner_dropdown_item) }
        windSpinner.setSelection(0)
        windSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                simulation.meanU = windPresets[position].meanU
                simulation.meanV = windPresets[position].meanV
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val algoSpinner = findViewById<Spinner>(R.id.spinnerAlgorithm)
        val algoLabels = CptAlgorithm.entries.map { it.label }
        algoSpinner.adapter = ArrayAdapter(this, R.layout.fluxo_spinner_item, algoLabels)
            .apply { setDropDownViewResource(R.layout.fluxo_spinner_dropdown_item) }
        algoSpinner.setSelection(CptAlgorithm.entries.indexOf(CptAlgorithm.FLUXOTAXIS))
        algoSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                simulation.algorithm = CptAlgorithm.entries[position]
                simulation.engine?.algorithm = CptAlgorithm.entries[position] // live, no reset needed
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        findViewById<View>(R.id.btnRun).setOnClickListener {
            if (simulationJob?.isActive == true) stopSimulation() else startSimulation()
        }
        findViewById<View>(R.id.btnGenerateMap).setOnClickListener {
            stopSimulation()
            simulation.generateNewMap()
            simulationView.invalidate()
        }
        findViewById<View>(R.id.btnResetSwarm).setOnClickListener {
            stopSimulation()
            simulation.resetSwarmOnly()
            simulationView.invalidate()
        }
        findViewById<View>(R.id.btnNewScenario).setOnClickListener {
            // Matches the Compose original: restarts placement on the SAME map (seed
            // unchanged), so you can compare algorithms/wind/placement fairly on one
            // layout. Generate New Map (above) is the one that randomizes the seed.
            stopSimulation()
            simulation.regenerateMap()
            simulationView.invalidate()
        }
    }

    private fun startSimulation() {
        if (simulation.engine == null) return // placement not finished yet
        if (simulationJob?.isActive == true) return
        findViewById<View>(R.id.btnRun).let { (it as android.widget.Button).text = "Pause" }
        simulationJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    simulation.step()
                } catch (ex: Exception) {
                    // Without this, an exception here would silently kill the coroutine --
                    // the button would still say "Pause" but nothing would progress, with
                    // no indication anything went wrong. Surface it instead.
                    withContext(Dispatchers.Main) {
                        simulation.reportError("Simulation error at step ${simulation.engine?.tickCount}: ${ex::class.simpleName}: ${ex.message}")
                        simulationView.invalidate()
                    }
                    stopSimulation()
                    break
                }
                withContext(Dispatchers.Main) {
                    simulationView.invalidate()
                }
                delay((1000L / speedStepsPerSecond.coerceIn(1, 60)))
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        findViewById<View>(R.id.btnRun).let { (it as android.widget.Button).text = "Run" }
    }

    override fun onPause() {
        super.onPause()
        stopSimulation()
    }
}
