package com.example.combinedsimulations

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.combinedsimulations.particle.ParticleFormationActivity
import com.example.combinedsimulations.coverage.UniformCoverageActivity
import com.example.combinedsimulations.newtonian.NewtonianFormationActivity
import com.example.combinedsimulations.kinetic.KineticTheoryActivity
import com.example.combinedsimulations.dinos.DinosActivity
import com.example.combinedsimulations.apo.ApoActivity
import com.example.combinedsimulations.perfect.PerfectFormationActivity
import com.example.combinedsimulations.fluxotaxis.FluxotaxisActivity

class MainMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        findViewById<android.view.View>(R.id.btnParticleFormation).setOnClickListener {
            startActivity(Intent(this, ParticleFormationActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnUniformCoverage).setOnClickListener {
            startActivity(Intent(this, UniformCoverageActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnNewtonianFormation).setOnClickListener {
            startActivity(Intent(this, NewtonianFormationActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnKineticTheory).setOnClickListener {
            startActivity(Intent(this, KineticTheoryActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnDinos).setOnClickListener {
            startActivity(Intent(this, DinosActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnApo).setOnClickListener {
            startActivity(Intent(this, ApoActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnPerfectFormation).setOnClickListener {
            startActivity(Intent(this, PerfectFormationActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnFluxotaxis).setOnClickListener {
            startActivity(Intent(this, FluxotaxisActivity::class.java))
        }
    }
}
