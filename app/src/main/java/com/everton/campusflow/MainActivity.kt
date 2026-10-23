package com.everton.campusflow

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // O dashboard usa fundo claro, inclusive quando o sistema está no modo escuro.
        val barsController = WindowCompat.getInsetsController(window, window.decorView)
        barsController.isAppearanceLightStatusBars = true
        barsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btnDisciplinas).setOnClickListener {
            // Mantém o dashboard na pilha para permitir o retorno.
            startActivity(Intent(this, DisciplinasActivity::class.java))
        }
    }
}
