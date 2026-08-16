package com.example.combinedsimulations.coverage

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.example.combinedsimulations.R
import kotlinx.coroutines.*

class UniformCoverageActivity : AppCompatActivity() {
    private lateinit var simulationView: CoverageSimulationView
    private lateinit var histogramView: HistogramView
    private lateinit var simulation: UniformCoverageSimulation
    private var simulationJob: Job? = null

    private var Delay = 33

    private val infoText = """
        WHAT IS IT?

        A group of agents tries to explore a square region as evenly as possible, combining a simple wander-and-avoid behavior with a physics-inspired idea borrowed from gas molecules: the mean free path, the average distance a particle travels before it collides with something.

        HOW IT WORKS

        Each agent moves forward until it senses a wall, another agent nearby, or has traveled its Mean Free Path Length, at which point it turns to a new random heading. Limiting how far an agent travels before turning, rather than letting it cross the whole region, produces much more even coverage of all nine regions of the square.

        HOW TO USE IT

        Tap Setup Agents, then Move Agents. The histogram below shows how evenly each of the nine regions has been visited - the colored bars should approach the yellow dashed "ideal" line over time. Mean Free Path Length controls how far an agent travels before turning; Delay controls simulation speed (lower is faster).
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uniform_coverage)

        simulationView = findViewById(R.id.simulationView)
        histogramView = findViewById(R.id.histogramView)
        simulation = UniformCoverageSimulation()
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
            if (simulationJob?.isActive != true) {
                startSimulation()
            }
        }

        findViewById<View>(R.id.btnStop).setOnClickListener {
            stopSimulation()
        }

        findViewById<Slider>(R.id.sliderParticles).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelParticles).text =
                "Number of Particles: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderMeanFreePath).addOnChangeListener { _, value, _ ->
            simulation.meanFreePathLength = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelMeanFreePath).text =
                "Mean Free Path Length: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderDelay).addOnChangeListener { _, value, _ ->
            Delay = value.toInt()
            findViewById<android.widget.TextView>(R.id.labelDelay).text =
                String.format("Delay: %d", value.toInt())
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
