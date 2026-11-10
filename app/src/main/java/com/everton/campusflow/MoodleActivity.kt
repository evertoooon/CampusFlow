package com.everton.campusflow

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.*
import androidx.lifecycle.*
import com.everton.campusflow.prazos.Prazos
import com.everton.campusflow.moodle.*
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts

class MoodleActivity : AppCompatActivity() {
    private val permissaoNovidades = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        atualizarPermissaoNovidades()
    }
    private fun atualizarPermissaoNovidades() {
        findViewById<TextView>(R.id.txtPermissaoNovidadesMoodle).setText(
            if (AvisoMoodle(this).permitido()) R.string.moodle_news_permission_ok else R.string.moodle_news_permission_note)
    }
    override fun onResume() { super.onResume(); atualizarPermissaoNovidades() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContentView(R.layout.activity_moodle)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val area = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(area.left, area.top, area.right, area.bottom); insets
        }
        val model = ViewModelProvider(this)[MoodleViewModel::class.java]
        findViewById<Button>(R.id.btnConflitosMoodle).setOnClickListener {
            startActivity(android.content.Intent(this,ConflitosMoodleActivity::class.java))
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            com.everton.campusflow.data.CampusFlowDatabase.getInstance(applicationContext).controleMoodleDao()
                .observarPendentes().collect { pendentes ->
                    findViewById<Button>(R.id.btnConflitosMoodle).apply {
                        text=getString(R.string.moodle_conflict_count,pendentes.size)
                        isEnabled=pendentes.isNotEmpty()
                    }
                }
        } }
        findViewById<Button>(R.id.btnPermissaoNovidadesMoodle).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissaoNovidades.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))
            }
        }
        lifecycleScope.launch { ConexaoMoodle.carregar(applicationContext) }
        findViewById<Button>(R.id.btnAtualizarConexaoMoodle).setOnClickListener {
            lifecycleScope.launch { ConexaoMoodle.atualizar(applicationContext, true) }
        }
        findViewById<Button>(R.id.btnDesconectarMoodle).setOnClickListener {
            lifecycleScope.launch { ConexaoMoodle.desconectar(applicationContext) }
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            ConexaoMoodle.estado.collect { s ->
                findViewById<TextView>(R.id.estadoConexaoMoodle).text = when {
                    s.atualizando -> getString(R.string.moodle_sync_running)
                    s.reconectar -> getString(R.string.moodle_sync_reconnect)
                    s.falhou -> getString(R.string.moodle_sync_failed)
                    !s.conectada -> getString(R.string.moodle_sync_off)
                    s.ultima == 0L -> getString(R.string.moodle_sync_ready)
                    else -> getString(R.string.moodle_sync_done, Prazos.formatar(s.ultima), s.novas) +
                        if(s.parcial) "\n" + getString(R.string.moodle_sync_partial) else ""
                }
                findViewById<Button>(R.id.btnAtualizarConexaoMoodle).isEnabled = s.conectada && !s.atualizando && !s.reconectar
                findViewById<Button>(R.id.btnDesconectarMoodle).isEnabled = s.conectada || s.reconectar
                if (s.falhaAgenda) findViewById<TextView>(R.id.estadoConexaoMoodle).append("\n" + getString(R.string.moodle_background_schedule_error))
                findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchSegundoPlanoMoodle).apply {
                    setOnCheckedChangeListener(null); isChecked=s.segundoPlano
                    isEnabled=s.conectada && !s.reconectar
                    setOnCheckedChangeListener { _, checked -> lifecycleScope.launch {
                        ConexaoMoodle.preferencias(applicationContext,segundoPlano=checked)
                    } }
                }
                findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAvisarNovasMoodle).apply {
                    setOnCheckedChangeListener(null); isChecked=s.avisarNovas
                    isEnabled=s.conectada && !s.reconectar
                    setOnCheckedChangeListener { _, checked -> lifecycleScope.launch {
                        ConexaoMoodle.preferencias(applicationContext,avisarNovas=checked)
                    } }
                }
            }
        } }
        val usuario = findViewById<TextInputEditText>(R.id.edtUsuarioMoodle)
        val senha = findViewById<TextInputEditText>(R.id.edtSenhaMoodle)
        val verificar = findViewById<Button>(R.id.btnVerificarMoodle)
        val limpar = findViewById<Button>(R.id.btnLimparMoodle)
        val resultado = findViewById<TextView>(R.id.txtResultadoMoodle)
        verificar.setOnClickListener {
            val u = usuario.text.toString().trim()
            val s = senha.text.toString()
            val inputUsuario = findViewById<TextInputLayout>(R.id.inputUsuarioMoodle)
            val inputSenha = findViewById<TextInputLayout>(R.id.inputSenhaMoodle)
            inputUsuario.error = if (u.isBlank()) getString(R.string.moodle_user_required) else null
            inputSenha.error = if (s.isEmpty()) getString(R.string.moodle_password_required) else null
            if (inputUsuario.error != null) { usuario.requestFocus(); return@setOnClickListener }
            if (inputSenha.error != null) { senha.requestFocus(); return@setOnClickListener }
            senha.text?.clear(); usuario.text?.clear()
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
            model.verificar(u, s)
        }
        limpar.setOnClickListener { senha.text?.clear(); usuario.text?.clear(); model.limpar() }
        findViewById<Button>(R.id.btnVoltarMoodle).setOnClickListener { finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.estado.collect { state ->
                    usuario.isEnabled = !state.ocupado
                    senha.isEnabled = !state.ocupado
                    verificar.isEnabled = !state.ocupado
                    limpar.isEnabled = !state.ocupado
                    verificar.setText(if (state.ocupado) R.string.moodle_loading else R.string.moodle_verify)
                    resultado.text = when {
                        state.ocupado -> getString(R.string.moodle_loading)
                        state.erro != null -> erro(state.erro)
                        state.resultado != null -> relatorio(state.resultado)
                        else -> getString(R.string.moodle_initial)
                    }
                    renderizarImportacao(model, state)
                }
            }
        }
    }
    private fun renderizarImportacao(model: MoodleViewModel, state: MoodleState) {
        val container = findViewById<LinearLayout>(R.id.selecoesMoodle)
        container.removeAllViews()
        val cursos = (state.resultado?.cursos as? ConsultaMoodle.Lida)?.dados.orEmpty()
        if (state.importado == null) cursos.forEach { curso ->
            val marcado = curso.id in state.escolhas
            val check = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
                text = getString(R.string.moodle_select_course, simples(curso.nome))
                setTextColor(getColor(R.color.cf_text_primary))
                minHeight = (48 * resources.displayMetrics.density).toInt()
                isChecked = marcado; isEnabled = !state.ocupado
                setOnCheckedChangeListener { _, checked -> model.escolher(curso.id, checked, state.escolhas[curso.id]) }
            }
            container.addView(check)
            if (marcado) {
                val ids = listOf<Long?>(null) + state.disciplinas.map { it.id }
                val labels = listOf(getString(R.string.moodle_create_course)) + state.disciplinas.map {
                    getString(R.string.moodle_link_course, it.nome, it.id)
                }
                container.addView(Spinner(this, Spinner.MODE_DROPDOWN).apply {
                    contentDescription = simples(curso.nome)
                    minimumHeight = (48 * resources.displayMetrics.density).toInt()
                    setPopupBackgroundResource(R.color.cf_surface)
                    adapter = ArrayAdapter(this@MoodleActivity, R.layout.item_opcao_disciplina_moodle, labels)
                    setSelection(ids.indexOf(state.escolhas[curso.id]).coerceAtLeast(0))
                    isEnabled = !state.ocupado && curso.id !in state.vinculados
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (ids[position] != model.estado.value.escolhas[curso.id]) model.escolher(curso.id, true, ids[position])
                        }
                    }
                })
            }
        }
        findViewById<Button>(R.id.btnConsultarMoodle).apply {
            visibility = if (cursos.isNotEmpty() && state.importado == null) View.VISIBLE else View.GONE
            isEnabled = !state.ocupado && state.escolhas.size in 1..20
            setOnClickListener { model.consultar() }
        }
        findViewById<TextView>(R.id.previaMoodle).text = state.importado?.let {
            getString(R.string.moodle_import_result, it.disciplinasCriadas, it.atividadesCriadas, it.repetidas) +
                "\n" + getString(R.string.moodle_updated_completion, it.conclusoesAtualizadas)
        } ?: state.previa?.let { previa -> buildString {
            appendLine(getString(R.string.moodle_preview_title))
            previa.forEach { p ->
                appendLine(); appendLine(getString(R.string.moodle_preview_course, simples(p.curso.nome),
                    p.tarefas.count { it.prazo != null }, p.tarefas.count { it.prazo == null }))
                if (p.tarefas.isEmpty()) appendLine(getString(R.string.moodle_preview_empty))
                if (p.modulosRestritos > 0) appendLine(getString(R.string.moodle_restricted_modules, p.modulosRestritos))
                p.tarefas.forEach { t ->
                    val status = getString(when(t.envio) {
                        EnvioMoodle.ENVIADO -> R.string.moodle_submitted
                        EnvioMoodle.RASCUNHO -> R.string.moodle_draft
                        EnvioMoodle.PENDENTE -> R.string.moodle_pending
                        EnvioMoodle.DESCONHECIDO -> R.string.moodle_unknown_submission
                    })
                    appendLine("• ${simples(t.nome)} — ${t.prazo?.let(Prazos::formatar) ?: getString(R.string.moodle_no_due)} — $status")
                }
            }
        } }.orEmpty()
        val professores = findViewById<LinearLayout>(R.id.professoresMoodle)
        professores.removeAllViews()
        if (state.importado == null) state.previa.orEmpty().forEach { p ->
            professores.addView(TextView(this).apply {
                text = getString(R.string.moodle_professor_course, simples(p.curso.nome))
                setTextColor(getColor(R.color.cf_text_primary)); textSize = 16f
            })
            professores.addView(TextView(this).apply {
                text = getString(when (p.professores.estado) {
                    EstadoProfessores.ENCONTRADOS -> R.string.moodle_professor_found
                    EstadoProfessores.SEM_IDENTIFICACAO -> R.string.moodle_professor_missing
                    EstadoProfessores.PARCIAL -> R.string.moodle_professor_partial
                    EstadoProfessores.NAO_LIBERADO -> R.string.moodle_professor_unavailable
                    EstadoProfessores.FALHOU -> R.string.moodle_professor_failed
                })
                setTextColor(getColor(R.color.cf_text_primary)); textSize = 16f
            })
            if (p.disciplinaId == null) {
                val input = TextInputLayout(this).apply {
                    hint = getString(R.string.moodle_professor_label)
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    boxBackgroundColor = getColor(R.color.cf_surface)
                }
                input.addView(TextInputEditText(input.context).apply {
                    isSaveEnabled = false
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    filters = arrayOf(android.text.InputFilter.LengthFilter(500))
                    minHeight = (56 * resources.displayMetrics.density).toInt()
                    setTextColor(getColor(R.color.cf_text_primary))
                    setText(model.professor(p)); isEnabled = !state.ocupado
                    doAfterTextChanged { model.editarProfessor(p.curso.id, it.toString()) }
                })
                professores.addView(input)
            } else professores.addView(TextView(this).apply {
                text = getString(R.string.moodle_professor_preserved)
                setTextColor(getColor(R.color.cf_text_primary)); textSize = 16f
            })
        }
        val atualizar = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.atualizarConclusoesMoodle)
        atualizar.visibility = if (state.previa != null && state.importado == null) View.VISIBLE else View.GONE
        atualizar.isEnabled = !state.ocupado
        if (state.previa == null) atualizar.isChecked = false
        val acompanhar = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.acompanharMoodle)
        acompanhar.visibility = atualizar.visibility
        acompanhar.isEnabled = !state.ocupado
        if (state.previa == null) acompanhar.isChecked = false
        findViewById<Button>(R.id.btnImportarMoodle).apply {
            visibility = if (state.previa != null && state.importado == null) View.VISIBLE else View.GONE
            isEnabled = !state.ocupado && state.erro == null
            setOnClickListener { model.importar(atualizar.isChecked, acompanhar.isChecked) }
        }
    }
    private fun erro(motivo: MotivoMoodle) = getString(when (motivo) {
        MotivoMoodle.CREDENCIAIS -> R.string.moodle_credentials_error
        MotivoMoodle.ACESSO -> R.string.moodle_access_error
        MotivoMoodle.REDE -> R.string.moodle_network_error
        MotivoMoodle.RESPOSTA -> R.string.moodle_response_error
    })
    private fun simples(texto: String) = HtmlCompat.fromHtml(texto, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    private fun relatorio(r: ResultadoMoodle): String = buildString {
        appendLine(getString(R.string.moodle_authenticated))
        when (val cursos = r.cursos) {
            is ConsultaMoodle.Lida -> {
                appendLine(getString(R.string.moodle_courses_count, cursos.dados.size))
            }
            ConsultaMoodle.NaoLiberada -> appendLine(getString(R.string.moodle_courses_unavailable))
            is ConsultaMoodle.Falhou -> appendLine(erro(cursos.motivo))
            ConsultaMoodle.NaoTestada -> Unit
        }
        appendLine()
        r.cursoAmostra?.let { appendLine(getString(R.string.moodle_sample_course, simples(it.nome))) }
        when (val tarefas = r.tarefas) {
            is ConsultaMoodle.Lida -> {
                appendLine(getString(R.string.moodle_tasks_count, tarefas.dados.size))
                tarefas.dados.take(5).forEach {
                    appendLine("• ${simples(it.nome)} — ${it.prazo?.let(Prazos::formatar) ?: getString(R.string.moodle_no_due)}")
                }
            }
            ConsultaMoodle.NaoLiberada -> appendLine(getString(R.string.moodle_tasks_unavailable))
            ConsultaMoodle.NaoTestada -> appendLine(getString(R.string.moodle_tasks_not_tested))
            is ConsultaMoodle.Falhou -> appendLine(erro(tarefas.motivo))
        }
        appendLine(); append(getString(R.string.moodle_result_note))
    }
}
