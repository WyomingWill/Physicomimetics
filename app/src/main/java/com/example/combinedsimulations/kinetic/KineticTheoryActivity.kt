package com.example.combinedsimulations.kinetic

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.example.combinedsimulations.R
import kotlinx.coroutines.*

class KineticTheoryActivity : AppCompatActivity() {
    private lateinit var simulationView: KineticSimulationView
    private lateinit var histogramView: VelocityHistogramView
    private lateinit var simulation: KineticTheorySimulation
    private var simulationJob: Job? = null

    private var Delay = 16

    private val infoText = """
        WHAT IS IT?

        A model of a physics phenomenon called Couette flow, using kinetic theory rather than forces: particles carry only kinetic energy, and their velocities change through collisions with neighbors and "kicks" from two moving walls, rather than through attraction or repulsion.

        HOW IT WORKS

        When two particles get close, they undergo a randomized collision that conserves total momentum and speed. Two walls (drawn as red and yellow zones) inject velocity in opposite directions, driving a sweeping flow through the corridor - similar to fluid trapped between two surfaces sliding past each other.

        HOW TO USE IT

        Tap Setup Agents, then Move Agents. Wall Velocity controls how fast the two walls move; Temperature controls how much random kinetic energy is present. The Speed slider controls how many milliseconds pass between simulation steps (lower is faster). The histogram shows the velocity profile across the corridor.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kinetic_theory)

        simulationView = findViewById(R.id.simulationView)
        histogramView = findViewById(R.id.histogramView)
        simulation = KineticTheorySimulation()
        simulationView.setSimulation(simulation)
        histogramView.setSimulation(simulation)

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
            val numParticles = findViewById<Slider>(R.id.sliderParticles).value.toInt()
            simulation.setup(numParticles)
            simulationView.invalidate()
            histogramView.invalidate()
        }

        findViewById<View>(R.id.btnStart).setOnClickListener {
            if (simulationJob?.isActive != true) startSimulation()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener { stopSimulation() }

        findViewById<View>(R.id.btnResample).setOnClickListener {
            simulation.resample()
            histogramView.invalidate()
        }

        findViewById<Slider>(R.id.sliderParticles).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelParticles).text =
                "Number of Particles: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderWallVelocity).addOnChangeListener { _, value, _ ->
            simulation.wallVelocity = value
            findViewById<android.widget.TextView>(R.id.labelWallVelocity).text =
                String.format("Wall Velocity: %.1f", value)
        }

        findViewById<Slider>(R.id.sliderTemperature).addOnChangeListener { _, value, _ ->
            simulation.temperature = value
            findViewById<android.widget.TextView>(R.id.labelTemperature).text =
                String.format("Temperature: %.3f", value)
        }

        findViewById<Slider>(R.id.sliderDelay).addOnChangeListener { _, value, _ ->
            Delay = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelDelay).text =
                String.format("Speed (Delay ms): %d", value.toInt())
        }
    }

    private fun startSimulation() {
        simulationJob = lifecycleScope.launch {
            while (isActive) {
                simulation.update()
                withContext(Dispatchers.Main) {
                    simulationView.invalidate()
                    histogramView.invalidate()
                }
                delay(Delay.toLong())
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
