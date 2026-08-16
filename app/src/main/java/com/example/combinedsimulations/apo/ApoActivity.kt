package com.example.combinedsimulations.apo

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.example.combinedsimulations.R
import kotlinx.coroutines.*

class ApoActivity : AppCompatActivity() {
    private lateinit var simulationView: ApoSimulationView
    private lateinit var simulation: ApoSimulation
    private var simulationJob: Job? = null
    private var ticksPerUpdate = 1

    private val infoText = """
        WHAT IS IT?

        Robots use the same self-organizing lattice force as the other formation models, but with an added twist: they are also searching for the minimum of a noisy mathematical function (the Rastrigan function), a classic hard optimization problem with many local dips that can trap a naive search.

        HOW IT WORKS

        Each pair of neighboring robots compares its own (noisy) fitness reading to its neighbor's. Whichever robot senses the better value pulls its neighbor toward it - an intentionally lopsided, not-equal-and-opposite force. The yellow-shaded landscape shows the function's value at every point; the blue dot marks the true optimum.

        HOW TO USE IT

        Tap Setup, then Move Robots. Touch anywhere on the landscape to move the optimum to that location. Noise controls how unreliable each fitness reading is; Zoom In reveals finer structure in the landscape (Reset Zoom returns to normal). Ticks per Update lets you run several simulation steps per screen update, since it can be slow at high robot counts. Distance to Optimum, shown on screen, tracks how close the formation's center of mass currently is to the true optimum.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apo)

        simulationView = findViewById(R.id.simulationView)
        simulation = ApoSimulation()
        simulationView.setSimulation(simulation)

        setupTabs()
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

    private fun setupControls() {
        findViewById<View>(R.id.btnSetup).setOnClickListener {
            stopSimulation()
            val count = findViewById<Slider>(R.id.sliderRobots).value.toInt()
            simulation.setup(count)
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnStart).setOnClickListener {
            if (simulationJob?.isActive != true) startSimulation()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener { stopSimulation() }

        findViewById<View>(R.id.btnClearPatches).setOnClickListener {
            simulation.clearPatches()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnZoomIn).setOnClickListener {
            simulation.zoomIn()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnResetZoom).setOnClickListener {
            simulation.resetZoom()
            simulationView.invalidate()
        }

        findViewById<Slider>(R.id.sliderRobots).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelRobots).text =
                "Number of Robots: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderForceMax).addOnChangeListener { _, value, _ ->
            simulation.forceMaximum = value
            findViewById<android.widget.TextView>(R.id.labelForceMax).text =
                String.format("Force Maximum: %.1f", value)
        }

        findViewById<Slider>(R.id.sliderFriction).addOnChangeListener { _, value, _ ->
            simulation.friction = value
            findViewById<android.widget.TextView>(R.id.labelFriction).text =
                String.format("Friction: %.2f", value)
        }

        findViewById<Slider>(R.id.sliderTimeStep).addOnChangeListener { _, value, _ ->
            simulation.timeStep = value
            findViewById<android.widget.TextView>(R.id.labelTimeStep).text =
                String.format("Time Step: %.2f", value)
        }

        findViewById<Slider>(R.id.sliderSeparation).addOnChangeListener { _, value, _ ->
            simulation.desiredSeparation = value
            findViewById<android.widget.TextView>(R.id.labelSeparation).text =
                "Desired Separation: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderNoise).addOnChangeListener { _, value, _ ->
            simulation.setNoise(value)
            findViewById<android.widget.TextView>(R.id.labelNoise).text =
                "Noise: " + value.toInt()
            simulationView.invalidate()
        }

        findViewById<Slider>(R.id.sliderTicksPerUpdate).addOnChangeListener { _, value, _ ->
            ticksPerUpdate = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelTicksPerUpdate).text =
                "Ticks per Update: " + value.toInt()
        }

        simulationView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val (sx, sy) = simulationView.screenToSim(event.x, event.y)
                simulation.moveOptimum(sx, sy)
                v.invalidate()
                true
            } else false
        }
    }

    private fun startSimulation() {
        simulationJob = lifecycleScope.launch {
            while (isActive) {
                // Recording granularity stays at 1 screen pixel per dot (unchanged from
                // the original). The 10x-more-dots effect comes entirely from the larger
                // trail cap below (ApoSimulation.kt, comTrail.size > 5000) -- the trail
                // just remembers 10x more travel distance now, at the same dot density.
                simulation.trailMinDistance = 1f / simulationView.getScale()
                repeat(ticksPerUpdate) {
                    simulation.step()
                }
                withContext(Dispatchers.Main) {
                    simulationView.invalidate()
                }
                delay(16)
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    override fun onPause() {
        super.onPause()
        stopSimulation()
    }
}
