package com.everton.campusflow

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AtividadesActivity : AppCompatActivity() {

    private lateinit var viewModel: AtividadesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_atividades)

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

        viewModel = ViewModelProvider(this)[AtividadesViewModel::class.java]
        findViewById<Button>(R.id.btnNovaAtividade).setOnClickListener {
            startActivity(Intent(this, AtividadeFormActivity::class.java))
        }

        findViewById<Button>(R.id.btnAbrirImportacao).setOnClickListener {
            startActivity(Intent(this, ImportarCalendarioActivity::class.java))
        }
        findViewById<Button>(R.id.btnVoltarResumoAtividades).setOnClickListener {
            finish()
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutAtividades)
        val viewPager = findViewById<ViewPager2>(R.id.viewPagerAtividades)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return if (position == 0) PendentesFragment() else ConcluidasFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) {
                getString(R.string.tab_pending)
            } else {
                getString(R.string.tab_completed)
            }
        }.attach()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    while (true) {
                        viewModel.atualizarHoje()
                        val agora = ZonedDateTime.now()
                        val amanha = agora.toLocalDate().plusDays(1).atStartOfDay(agora.zone)
                        // Reavalia na meia-noite; mudanças de hora/fuso são percebidas em até um minuto.
                        delay(Duration.between(agora, amanha).toMillis().coerceIn(1_000L, 60_000L))
                    }
                }
                launch {
                    viewModel.estado.collect { estado ->
                        if (estado.erroAtualizacaoId != null || estado.avisoLembrete) {
                            Toast.makeText(this@AtividadesActivity,
                                if (estado.avisoLembrete) R.string.reminder_schedule_error else
                                    R.string.activity_status_update_error, Toast.LENGTH_LONG).show()
                            viewModel.limparErroAtualizacao()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizarHoje()
        lifecycleScope.launch {
            try { com.everton.campusflow.lembretes.Lembretes.reconciliar(applicationContext)
            } catch (cancelamento: kotlinx.coroutines.CancellationException) { throw cancelamento
            } catch (_: Exception) { Toast.makeText(this@AtividadesActivity, R.string.reminder_schedule_error, Toast.LENGTH_LONG).show() }
        }
    }
}
