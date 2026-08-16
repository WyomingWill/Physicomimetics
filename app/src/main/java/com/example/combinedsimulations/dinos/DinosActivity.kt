package com.example.combinedsimulations.dinos

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.example.combinedsimulations.R
import kotlinx.coroutines.*

class DinosActivity : AppCompatActivity() {
    private lateinit var simulationView: DinosSimulationView
    private lateinit var simulation: DinosSimulation
    private var dinoJob: Job? = null
    private var droneJob: Job? = null

    private val infoText = """
        WHAT IS IT?

        Aquatic drones try to reach a goal while either minimizing or maximizing their exposure to a glowing chemical trail left behind by simulated dinoflagellates (bioluminescent organisms) - modeling a real task where a formation must move stealthily, or in the opposite case, thoroughly map a hazardous area.

        HOW IT WORKS

        Dinoflagellates wander using simple slime-mold-style rules, depositing chemical as they move; the chemical then diffuses and slowly evaporates. Drones self-organize into a lattice using the split-Newtonian force law, plus a secondary force that nudges each pair of drones toward whichever one is currently sensing a better (lower, or higher, depending on the mode) chemical reading.

        HOW TO USE IT

        Tap Setup, then Run Dinos to let the chemical trail build up (animates 100 steps), then Move Drones to send the formation toward the goal - it stops automatically when a drone arrives. Toggle Min/Max switches between minimizing and maximizing chemical exposure. Toggle Goal turns the pull toward the goal on and off.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dinos)

        simulationView = findViewById(R.id.simulationView)
        simulation = DinosSimulation()
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
            stopDinoRun()
            stopDroneRun()
            simulation.numberOfDrones = findViewById<Slider>(R.id.sliderDrones).value.toInt()
            simulation.numberOfDinos = findViewById<Slider>(R.id.sliderDinos).value.toInt()
            simulation.randomSeed = findViewById<Slider>(R.id.sliderSeed).value.toInt()
            simulation.setup()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnRunDinos).setOnClickListener {
            startDinoRun()
        }

        findViewById<View>(R.id.btnMoveDrones).setOnClickListener {
            startDroneRun()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener {
            stopDinoRun()
            stopDroneRun()
        }

        findViewById<View>(R.id.btnToggleGoal).setOnClickListener {
            simulation.toggleGoal()
        }

        findViewById<View>(R.id.btnToggleMinMax).setOnClickListener {
            simulation.toggleMinMax()
        }

        findViewById<Slider>(R.id.sliderDrones).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelDrones).text =
                "Number of Drones: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderDinos).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelDinos).text =
                "Number of Dinos: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderSeed).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelSeed).text =
                "Random Seed: " + value.toInt()
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

        findViewById<Slider>(R.id.sliderGoalForce).addOnChangeListener { _, value, _ ->
            simulation.goalForce = value
            findViewById<android.widget.TextView>(R.id.labelGoalForce).text =
                String.format("Goal Force: %.2f", value)
        }
    }

    private fun startDinoRun() {
        if (dinoJob?.isActive == true) return
        dinoJob = lifecycleScope.launch {
            repeat(100) {
                simulation.dinoStep()
                withContext(Dispatchers.Main) { simulationView.invalidate() }
                delay(16)
            }
        }
    }

    private fun stopDinoRun() {
        dinoJob?.cancel()
        dinoJob = null
    }

    private fun startDroneRun() {
        if (droneJob?.isActive == true) return
        droneJob = lifecycleScope.launch {
            while (isActive) {
                val done = simulation.moveDronesStep()
                withContext(Dispatchers.Main) { simulationView.invalidate() }
                if (done) break
                delay(16)
            }
        }
    }

    private fun stopDroneRun() {
        droneJob?.cancel()
        droneJob = null
    }

    override fun onPause() {
        super.onPause()
        stopDinoRun()
        stopDroneRun()
    }
}
