package com.example.combinedsimulations.perfect

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.example.combinedsimulations.R
import kotlinx.coroutines.*

class PerfectFormationActivity : AppCompatActivity() {
    private lateinit var simulationView: SimulationView
    private lateinit var simulation: ParticleSimulation
    private var simulationJob: Job? = null

    private val infoText = """
        WHAT IS IT?

        An extension of the Split Newtonian Formation model, from Chapter 4 of "Physicomimetics: Physics-Based Swarm Intelligence." Instead of letting a lattice emerge purely from local rules, this model deliberately engineers a perfect, defect-free square or triangular lattice.

        HOW IT WORKS

        Each particle is given a fixed (m, n) address at creation, indicating where it should ideally sit relative to its neighbors, and all particles are assumed to share a common compass heading - a global coordinate frame the other formation models don't require. The force law is deliberately modified to break Newton's third law by a small degree: rather than reacting with pure equal-and-opposite forces, a particle pulls harder on a neighbor that is out of place relative to its ideal address. This is intentional - physicomimetics is meant to "mimic" physics for the sake of performance, not copy it exactly. One consequence: linear momentum is still conserved, but angular momentum is not conserved for triangular lattices.

        HOW TO USE IT

        Tap Setup Agents, then Move Agents. Number of Particles only takes effect on the next Setup Agents; to grow the swarm mid-run instead, use One is Born, which clones a random existing particle nearby. Toggle Formation switches between triangular and square target lattices - watch how the system tears apart and self-repairs. Clear removes the accumulated red center-of-mass trail. The red dot marks the center of mass; if linear momentum is truly conserved, it should barely move.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfect_formation)

        simulationView = findViewById(R.id.simulationView)
        simulation = ParticleSimulation()
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
            val count = findViewById<Slider>(R.id.sliderParticles).value.toInt()
            simulation.setup(count)
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnStart).setOnClickListener {
            if (simulationJob?.isActive != true) startSimulation()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener { stopSimulation() }

        findViewById<View>(R.id.btnClear).setOnClickListener {
            simulation.clearTrail()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnAddParticle).setOnClickListener {
            simulation.addParticle()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnToggleFormation).setOnClickListener {
            simulation.toggleFormation()
        }

        findViewById<Slider>(R.id.sliderParticles).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelParticles).text =
                "Number of Particles: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderGravitationalConstant).addOnChangeListener { _, value, _ ->
            simulation.gravitationalConstant = value
            findViewById<android.widget.TextView>(R.id.labelGravitationalConstant).text =
                "Gravitational Constant: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderPower).addOnChangeListener { _, value, _ ->
            simulation.power = value
            findViewById<android.widget.TextView>(R.id.labelPower).text =
                String.format("Power: %.1f", value)
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
    }

    private fun startSimulation() {
        simulationJob = lifecycleScope.launch {
            while (isActive) {
                simulation.update()
                withContext(Dispatchers.Main) { simulationView.invalidate() }
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
