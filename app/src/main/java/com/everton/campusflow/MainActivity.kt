package com.everton.campusflow

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.everton.campusflow.lembretes.Lembretes
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            com.everton.campusflow.moodle.ConexaoMoodle.carregar(applicationContext)
            com.everton.campusflow.moodle.ConexaoMoodle.atualizar(applicationContext)
        }
        lifecycleScope.launch {
            try { Lembretes.reconciliar(applicationContext)
            } catch (cancelamento: CancellationException) { throw cancelamento
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, R.string.reminder_schedule_error, Toast.LENGTH_LONG).show()
            }
        }
    }
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
        findViewById<Button>(R.id.btnMoodle).setOnClickListener {
            startActivity(Intent(this, MoodleActivity::class.java))
        }
        val model = ViewModelProvider(this)[AtividadesViewModel::class.java]
        val pendentes = findViewById<TextView>(R.id.txtPendentes)
        val concluidas = findViewById<TextView>(R.id.txtConcluidas)
        val atrasadas = findViewById<TextView>(R.id.txtAtrasadas)
        val titulo = findViewById<TextView>(R.id.txtTituloResumoAtividades)
        val descricao = findViewById<TextView>(R.id.txtDescricaoResumoAtividades)
        val tentar = findViewById<Button>(R.id.btnRecarregarResumo)
        tentar.setOnClickListener { model.carregarAtividades() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    while (true) {
                        model.atualizarHoje()
                        val agora = ZonedDateTime.now()
                        val amanha = agora.toLocalDate().plusDays(1).atStartOfDay(agora.zone)
                        delay(Duration.between(agora, amanha).toMillis().coerceIn(1_000L, 60_000L))
                    }
                }
                launch {
                    model.estado.collect { state ->
                        val disponivel = !state.carregando && !state.erro
                        tentar.visibility = if (state.erro) View.VISIBLE else View.GONE
                        if (disponivel) {
                            val hoje = LocalDate.ofEpochDay(state.hojeEpochDay)
                            pendentes.text = getString(R.string.dashboard_pending_count, state.pendentes.size)
                            concluidas.text = getString(R.string.dashboard_completed_count, state.concluidas.size)
                            atrasadas.text = getString(R.string.dashboard_overdue_count,
                                state.pendentes.count { it.estaAtrasada(hoje, state.agoraEpochMillis) })
                            val vazio = state.pendentes.isEmpty() && state.concluidas.isEmpty()
                            titulo.setText(if (vazio) R.string.dashboard_empty_title else R.string.dashboard_has_activities)
                            descricao.setText(if (vazio) R.string.dashboard_empty_description else R.string.dashboard_open_list)
                        } else {
                            // Não mostra zero nem números antigos como se fossem dados confirmados.
                            val texto = getString(if (state.erro) R.string.dashboard_counts_unavailable else R.string.activities_loading)
                            pendentes.text = texto
                            concluidas.text = texto
                            atrasadas.text = texto
                            titulo.setText(if (state.erro) R.string.activities_load_error else R.string.activities_loading)
                            descricao.setText(if (state.erro) R.string.dashboard_retry_description else R.string.dashboard_loading_description)
                        }
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnAtividades).setOnClickListener {
            startActivity(Intent(this, AtividadesActivity::class.java))
        }
    }
}
