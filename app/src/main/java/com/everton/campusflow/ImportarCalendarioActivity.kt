package com.everton.campusflow

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.*
import com.everton.campusflow.lembretes.Notificacoes
import com.everton.campusflow.importacao.PrazosIcs
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ImportarCalendarioActivity : AppCompatActivity() {
    private lateinit var model: ImportarCalendarioViewModel
    private val escolher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.carregar(it) }
    }
    private val pedir = registerForActivityResult(ActivityResultContracts.RequestPermission()) { atualizarPermissao() }
    private fun atualizarPermissao() {
        if (!::model.isInitialized) return
        findViewById<TextView>(R.id.txtPermissaoImportacao).setText(if (Notificacoes(this).habilitado())
            R.string.reminder_permission_enabled else R.string.reminder_permission_disabled)
    }
    override fun onResume() { super.onResume(); atualizarPermissao() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_importar_calendario)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val area = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(area.left, area.top, area.right, area.bottom); insets
        }
        model = ViewModelProvider(this)[ImportarCalendarioViewModel::class.java]
        val arquivo = findViewById<Button>(R.id.btnEscolherCalendario)
        val salvar = findViewById<Button>(R.id.btnImportarEventos)
        val lembrar = findViewById<MaterialSwitch>(R.id.switchImportarLembretes)
        val voltar = findViewById<Button>(R.id.btnVoltarImportacao)
        val disciplina = findViewById<Button>(R.id.btnDisciplinaImportacao)
        val permissao = findViewById<Button>(R.id.btnPermissaoImportacao)
        arquivo.setOnClickListener { escolher.launch(arrayOf("*/*")) }
        salvar.setOnClickListener { model.importar() }
        lembrar.setOnCheckedChangeListener { _, ativo -> if (model.estado.value.lembrar != ativo) model.lembrar(ativo) }
        voltar.setOnClickListener { finish() }
        disciplina.setOnClickListener { startActivity(Intent(this, DisciplinaFormActivity::class.java)) }
        permissao.setOnClickListener {
            val prefs = getSharedPreferences("lembretes", MODE_PRIVATE)
            val falta = Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (falta && (!prefs.getBoolean("permissao_pedida", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                prefs.edit().putBoolean("permissao_pedida", true).apply()
                pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var renderizado: Pair<List<com.everton.campusflow.importacao.EventoIcs>, List<com.everton.campusflow.data.Disciplina>>? = null
                val spinners = mutableMapOf<String, Spinner>()
                model.estado.collect { state ->
                    val editavel = !state.ocupado && state.resultado == null
                    arquivo.isEnabled = editavel
                    salvar.isEnabled = editavel && state.selecoes.isNotEmpty() && state.disciplinas.isNotEmpty()
                    lembrar.isEnabled = editavel
                    lembrar.isChecked = state.lembrar
                    voltar.isEnabled = !state.ocupado
                    disciplina.isEnabled = editavel
                    disciplina.visibility = if (state.disciplinas.isEmpty()) View.VISIBLE else View.GONE
                    permissao.visibility = if (state.lembrar) View.VISIBLE else View.GONE
                    findViewById<View>(R.id.txtPermissaoImportacao).visibility = permissao.visibility
                    atualizarPermissao()
                    findViewById<TextView>(R.id.txtStatusImportacao).apply {
                        text = when {
                            state.resultado != null -> getString(R.string.import_result,
                                state.resultado.ids.size, state.resultado.repetidos) +
                                if (state.aviso) "\n${getString(R.string.reminder_schedule_error)}" else ""
                            state.mensagem != null -> getString(state.mensagem)
                            state.ocupado -> getString(R.string.import_busy)
                            else -> getString(R.string.import_preview_count, state.eventos.size, state.ignorados)
                        }
                    }
                    val chave = state.eventos to state.disciplinas
                    if (renderizado != chave) {
                        renderizado = chave
                        val lista = findViewById<LinearLayout>(R.id.listaPreviaCalendario)
                        lista.removeAllViews(); spinners.clear()
                        val nomes = listOf(getString(R.string.import_skip)) + state.disciplinas.map {
                            "${it.nome} — ${it.professor.ifBlank { getString(R.string.import_no_teacher) }} (#${it.id})"
                        }
                        state.eventos.forEach { evento ->
                            val bloco = layoutInflater.inflate(R.layout.item_previa_calendario, lista, false)
                            bloco.findViewById<TextView>(R.id.txtEventoCalendario).text = evento.titulo
                            bloco.findViewById<TextView>(R.id.txtPrazoCalendario).text = evento.instante?.let {
                                getString(R.string.import_event_time, PrazosIcs.formatar(it))
                            } ?: getString(R.string.import_event_day, LocalDate.ofEpochDay(evento.dia)
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                            val spinner = bloco.findViewById<Spinner>(R.id.spinnerDisciplinaCalendario)
                            spinner.contentDescription = getString(R.string.import_associate, evento.titulo)
                            spinner.adapter = ArrayAdapter(this@ImportarCalendarioActivity, android.R.layout.simple_spinner_dropdown_item, nomes)
                            spinner.setSelection(state.disciplinas.indexOfFirst { it.id == state.selecoes[evento.uid] } + 1)
                            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                    val selecionada = state.disciplinas.getOrNull(position - 1)?.id
                                    if (model.estado.value.selecoes[evento.uid] != selecionada) model.selecionar(evento.uid, selecionada)
                                }
                            }
                            spinners[evento.uid] = spinner
                            lista.addView(bloco)
                        }
                    }
                    spinners.forEach { (uid, spinner) ->
                        spinner.isEnabled = editavel && state.disciplinas.isNotEmpty()
                        val posicao = state.disciplinas.indexOfFirst { it.id == state.selecoes[uid] } + 1
                        if (spinner.selectedItemPosition != posicao) spinner.setSelection(posicao)
                    }
                }
            }
        }
    }
}
