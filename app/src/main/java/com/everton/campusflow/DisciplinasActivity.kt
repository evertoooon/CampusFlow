package com.everton.campusflow

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.everton.campusflow.data.CampusFlowDatabase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DisciplinasActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_disciplinas)

        val barsController = WindowCompat.getInsetsController(window, window.decorView)
        barsController.isAppearanceLightStatusBars = true
        barsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }

        findViewById<Button>(R.id.btnNovaDisciplina).setOnClickListener {
            startActivity(Intent(this, DisciplinaFormActivity::class.java))
        }

        findViewById<Button>(R.id.btnVoltarResumo).setOnClickListener {
            // Encerra esta tela e revela o dashboard que já está na pilha.
            finish()
        }

        val lista = findViewById<LinearLayout>(R.id.listaDisciplinas)
        val vazio = findViewById<View>(R.id.estadoVazioDisciplinas)
        val status = findViewById<TextView>(R.id.txtStatusDisciplinas)
        val dao = CampusFlowDatabase.getInstance(applicationContext).disciplinaDao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Ao voltar do formulário, observa novamente os dados persistidos.
                status.setText(R.string.disciplines_loading)
                status.visibility = View.VISIBLE
                dao.observarTodas()
                    .catch {
                        lista.visibility = View.GONE
                        vazio.visibility = View.GONE
                        status.setText(R.string.disciplines_load_error)
                    }
                    .collect { disciplinas ->
                        status.visibility = View.GONE
                        vazio.visibility = if (disciplinas.isEmpty()) View.VISIBLE else View.GONE
                        lista.visibility = if (disciplinas.isEmpty()) View.GONE else View.VISIBLE
                        lista.removeAllViews()
                        disciplinas.forEach { disciplina ->
                            val item = layoutInflater.inflate(R.layout.item_disciplina, lista, false)
                            item.setOnClickListener {
                                startActivity(
                                    Intent(this@DisciplinasActivity, DisciplinaFormActivity::class.java)
                                        .putExtra(DisciplinaFormActivity.EXTRA_DISCIPLINA_ID, disciplina.id),
                                )
                            }
                            item.findViewById<TextView>(R.id.txtNomeDisciplina).text = disciplina.nome
                            item.findViewById<TextView>(R.id.txtProfessorDisciplina).apply {
                                visibility = if (disciplina.professor.isEmpty()) View.GONE else View.VISIBLE
                                text = getString(R.string.discipline_teacher_format, disciplina.professor)
                            }
                            lista.addView(item)
                        }
                    }
            }
        }
    }
}
