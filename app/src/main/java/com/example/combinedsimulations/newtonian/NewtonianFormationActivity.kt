package com.example.combinedsimulations.newtonian

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.example.combinedsimulations.R
import kotlinx.coroutines.*

class NewtonianFormationActivity : AppCompatActivity() {
    private lateinit var simulationView: SimulationView
    private lateinit var simulation: ParticleSimulation
    private var simulationJob: Job? = null

    private val infoText = """
        WHAT IS IT?

        Like Particle Formation, but particles self-organize using a "split Newtonian" force law - attractive when farther than the desired separation, repulsive when closer, following an inverse-square relationship similar to gravity.

        HOW IT WORKS

        The Gravitational Constant, Power, and Force Maximum sliders control the strength and shape of the force. A phase-transition threshold (shown on screen as "G Phase Transition") indicates roughly how strong the force needs to be to form a stable lattice at the current settings.

        HOW TO USE IT

        Tap Setup, then Start. Touch the screen to place obstacles. Toggle Formation switches triangular and square lattices; Toggle Goal enables a pull toward a fixed point. Add, Remove, and Disable adjust particle count live.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newtonian_formation)

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
            val numParticles = findViewById<Slider>(R.id.sliderParticles).value.toInt()
            simulation.setup(numParticles)
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnStart).setOnClickListener {
            if (simulationJob?.isActive != true) startSimulation()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener { stopSimulation() }

        findViewById<View>(R.id.btnClear).setOnClickListener {
            simulation.clearObstacles()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnToggleFormation).setOnClickListener { simulation.toggleFormation() }
        findViewById<View>(R.id.btnToggleGoal).setOnClickListener { simulation.toggleGoal() }

        findViewById<View>(R.id.btnAddParticle).setOnClickListener {
            simulation.addParticle()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnRemoveParticle).setOnClickListener {
            simulation.removeParticle()
            simulationView.invalidate()
        }

        findViewById<View>(R.id.btnDisableParticle).setOnClickListener {
            simulation.disableParticle()
            simulationView.invalidate()
        }

        findViewById<Slider>(R.id.sliderParticles).addOnChangeListener { _, value, _ ->
            findViewById<android.widget.TextView>(R.id.labelParticles).text =
                "Number of Particles: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderGravitationalConstant).addOnChangeListener { _, value, _ ->
            simulation.params.gravitationalConstant = value
            findViewById<android.widget.TextView>(R.id.labelGravitationalConstant).text =
                "Gravitational Constant: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderPower).addOnChangeListener { _, value, _ ->
            simulation.params.power = value
            findViewById<android.widget.TextView>(R.id.labelPower).text =
                String.format("Power: %.1f", value)
        }

        findViewById<Slider>(R.id.sliderForceMax).addOnChangeListener { _, value, _ ->
            simulation.params.forceMaximum = value
            findViewById<android.widget.TextView>(R.id.labelForceMax).text =
                String.format("Force Maximum: %.1f", value)
        }

        findViewById<Slider>(R.id.sliderFriction).addOnChangeListener { _, value, _ ->
            simulation.params.friction = value
            findViewById<android.widget.TextView>(R.id.labelFriction).text =
                String.format("Friction: %.2f", value)
        }

        findViewById<Slider>(R.id.sliderTimeStep).addOnChangeListener { _, value, _ ->
            simulation.params.timeStep = value
            findViewById<android.widget.TextView>(R.id.labelTimeStep).text =
                String.format("Time Step: %.2f", value)
        }

        findViewById<Slider>(R.id.sliderSeparation).addOnChangeListener { _, value, _ ->
            simulation.params.desiredSeparation = value
            findViewById<android.widget.TextView>(R.id.labelSeparation).text =
                "Desired Separation: " + value.toInt()
        }

        findViewById<Slider>(R.id.sliderGoalForce).addOnChangeListener { _, value, _ ->
            simulation.params.goalForce = value
            findViewById<android.widget.TextView>(R.id.labelGoalForce).text =
                String.format("Goal Force: %.2f", value)
        }

        findViewById<Slider>(R.id.sliderObstacleSize).addOnChangeListener { _, value, _ ->
            simulation.params.obstacleSize = value
            findViewById<android.widget.TextView>(R.id.labelObstacleSize).text =
                "Obstacle Size: " + value.toInt()
        }

        simulationView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                simulationView.addObstacleAtScreenPosition(event.x, event.y)
                v.invalidate()
                true
            } else false
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
