package com.everton.campusflow

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.os.Build
import android.provider.Settings
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.everton.campusflow.data.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.everton.campusflow.lembretes.*

class AtividadeFormActivity : AppCompatActivity() {
    companion object { const val EXTRA_ATIVIDADE_ID = "atividade_id" }
    private var atualizarNotificacoes: () -> Unit = {}
    private val pedirNotificacoes = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        atualizarNotificacoes()
    }

    override fun onResume() {
        super.onResume()
        atualizarNotificacoes()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_atividade_form)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val area = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(area.left, area.top, area.right, area.bottom)
            insets
        }
        val model = ViewModelProvider(this)[AtividadeFormViewModel::class.java]
        val titulo = findViewById<TextInputEditText>(R.id.edtTituloAtividade)
        val descricao = findViewById<TextInputEditText>(R.id.edtDescricaoAtividade)
        val disciplina = findViewById<MaterialAutoCompleteTextView>(R.id.seletorDisciplina)
        val tipo = findViewById<MaterialAutoCompleteTextView>(R.id.seletorTipo)
        val prioridade = findViewById<MaterialAutoCompleteTextView>(R.id.seletorPrioridade)
        val prazo = findViewById<Button>(R.id.btnPrazoAtividade)
        val salvar = findViewById<Button>(R.id.btnSalvarAtividade)
        val excluir = findViewById<Button>(R.id.btnExcluirAtividade)
        val voltar = findViewById<Button>(R.id.btnVoltarAtividades)
        val cadastrarDisciplina = findViewById<Button>(R.id.btnCadastrarDisciplinaAtividade)
        val status = findViewById<TextView>(R.id.txtStatusFormAtividade)
        val lembrar = findViewById<CompoundButton>(R.id.switchLembrete)
        val escolherLembrete = findViewById<Button>(R.id.btnHorarioLembrete)
        val permissao = findViewById<Button>(R.id.btnPermissaoLembrete)
        val statusPermissao = findViewById<TextView>(R.id.txtPermissaoLembrete)
        val relogioLembrete = findViewById<TextView>(R.id.txtRelogioLembrete)
        val formatoRelogio = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss VV")
        fun referenciaRelogio(): String = java.time.ZonedDateTime.now().format(formatoRelogio)
        val notificacoes = Notificacoes(applicationContext)
        atualizarNotificacoes = {
            statusPermissao.setText(if (notificacoes.habilitado()) R.string.reminder_permission_enabled
                else R.string.reminder_permission_disabled)
            if (notificacoes.habilitado()) lifecycleScope.launch {
                try { Lembretes.reconciliar(applicationContext)
                } catch (cancelamento: CancellationException) { throw cancelamento
                } catch (_: Exception) {
                    Toast.makeText(this@AtividadeFormActivity, R.string.reminder_schedule_error, Toast.LENGTH_LONG).show()
                }
            }
        }
        permissao.setOnClickListener {
            val prefs = getSharedPreferences("lembretes", MODE_PRIVATE)
            val jaPediu = prefs.getBoolean("permissao_pedida", false)
            val faltaPermissao = Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (faltaPermissao && (!jaPediu || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                prefs.edit().putBoolean("permissao_pedida", true).apply()
                pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        findViewById<TextView>(R.id.txtTituloFormAtividade).setText(
            if (model.editando) R.string.activity_edit_title else R.string.activity_new_title)
        val tipos = TipoAtividade.entries
        val prioridades = PrioridadeAtividade.entries
        val tiposTexto = listOf(R.string.activity_type_prova, R.string.activity_type_trabalho,
            R.string.activity_type_tarefa).map { getString(it) }
        val prioridadesTexto = listOf(R.string.activity_priority_baixa, R.string.activity_priority_media,
            R.string.activity_priority_alta).map { getString(it) }
        fun adapter(values: List<String>) = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, values)
        tipo.setAdapter(adapter(tiposTexto))
        prioridade.setAdapter(adapter(prioridadesTexto))
        val automaticos = findViewById<Button>(R.id.btnUsarAutomaticos)
        automaticos.setOnClickListener { model.rascunho(automatico = true, lembrar = true) }
        var preenchendo = false
        lembrar.setOnCheckedChangeListener { _, ativo -> if (!preenchendo) model.rascunho(lembrar = ativo) }
        escolherLembrete.setOnClickListener {
            relogioLembrete.text = getString(R.string.reminder_device_clock, referenciaRelogio())
            val agora = java.time.ZonedDateTime.now()
            val atual = model.estado.value.lembrete?.takeIf { it > agora.toInstant().toEpochMilli() }
                ?.let { Instant.ofEpochMilli(it).atZone(agora.zone) }
                ?: agora.plusMinutes(5).withSecond(0).withNano(0)
            DatePickerDialog(this, { _, ano, mes, dia ->
                TimePickerDialog(this, { _, hora, minuto ->
                    val local = LocalDateTime.of(ano, mes + 1, dia, hora, minuto)
                    val referencia = Instant.now()
                    when (val resultado = HorarioLembrete.validar(local, ZoneId.systemDefault(), referencia)) {
                        is HorarioSelecionado.Valido -> model.rascunho(lembrete = resultado.epochMillis)
                        HorarioSelecionado.Passado -> Toast.makeText(this,
                            getString(R.string.reminder_not_future_format, referenciaRelogio()), Toast.LENGTH_LONG).show()
                        HorarioSelecionado.InexistenteNoFuso -> Toast.makeText(this,
                            R.string.reminder_invalid_zone_time, Toast.LENGTH_LONG).show()
                    }
                    relogioLembrete.text = getString(R.string.reminder_device_clock, referenciaRelogio())
                }, atual.hour, atual.minute, true).apply {
                    setTitle(getString(R.string.reminder_time_picker_title, referenciaRelogio()))
                }.show()
            }, atual.year, atual.monthValue - 1, atual.dayOfMonth).show()
        }
        var disciplinas = emptyList<Disciplina>()
        var rotulos = emptyList<String>()
        titulo.doAfterTextChanged { if (!preenchendo) model.rascunho(titulo = it.toString()) }
        descricao.doAfterTextChanged { if (!preenchendo) model.rascunho(descricao = it.toString()) }
        disciplina.setOnItemClickListener { _, _, position, _ ->
            model.rascunho(disciplinaId = disciplinas[position].id)
        }
        tipo.setOnItemClickListener { _, _, position, _ -> model.rascunho(tipo = tipos[position]) }
        prioridade.setOnItemClickListener { _, _, position, _ -> model.rascunho(prioridade = prioridades[position]) }
        prazo.setOnClickListener {
            val data = model.estado.value.prazo?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
            DatePickerDialog(this, { _, ano, mes, dia ->
                model.rascunho(prazo = LocalDate.of(ano, mes + 1, dia).toEpochDay())
            }, data.year, data.monthValue - 1, data.dayOfMonth).show()
        }
        salvar.setOnClickListener {
            val input = findViewById<TextInputLayout>(R.id.inputTituloAtividade)
            input.error = if (titulo.text.isNullOrBlank()) getString(R.string.activity_title_required) else null
            if (input.error != null) titulo.requestFocus()
            model.salvar()
        }
        voltar.setOnClickListener { finish() }
        cadastrarDisciplina.setOnClickListener { startActivity(Intent(this, DisciplinaFormActivity::class.java)) }
        excluir.visibility = if (model.editando) View.VISIBLE else View.GONE
        excluir.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.activity_delete_title)
                .setMessage(getString(R.string.activity_delete_confirmation, model.tituloOriginal))
                .setNegativeButton(R.string.discipline_delete_cancel, null)
                .setPositiveButton(R.string.discipline_delete_confirm) { _, _ -> model.excluir() }.show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.estado.collect { state ->
                    val editavel = state.pronto && !state.ocupado && !state.inexistente && !state.finalizado
                    listOf(titulo, descricao, tipo, prioridade, prazo).forEach { it.isEnabled = editavel }
                    prazo.isEnabled = editavel && state.prazoExato == null
                    findViewById<View>(R.id.txtPrazoImportado).visibility = if (state.prazoExato == null) View.GONE else View.VISIBLE
                    disciplina.isEnabled = editavel && state.disciplinasCarregadas && state.disciplinas.isNotEmpty()
                    salvar.isEnabled = editavel && state.disciplinasCarregadas && state.disciplinas.isNotEmpty()
                    excluir.isEnabled = editavel
                    voltar.isEnabled = !state.ocupado
                    cadastrarDisciplina.isEnabled = !state.ocupado
                    cadastrarDisciplina.visibility = if (state.disciplinasCarregadas && state.disciplinas.isEmpty()) View.VISIBLE else View.GONE
                    if (disciplinas != state.disciplinas) {
                        disciplinas = state.disciplinas
                        val bases = disciplinas.map { d -> if (d.professor.isBlank()) d.nome else
                            getString(R.string.activity_discipline_option, d.nome, d.professor) }
                        val repetidos = bases.groupingBy { it }.eachCount()
                        rotulos = bases.mapIndexed { i, label -> if (repetidos[label]!! > 1)
                            getString(R.string.activity_discipline_disambiguation, label, disciplinas[i].id) else label }
                        disciplina.setAdapter(adapter(rotulos))
                    }
                    preenchendo = true
                    lembrar.isChecked = state.lembrar
                    findViewById<TextView>(R.id.txtExplicacaoLembrete).setText(if (state.prazoExato == null)
                        R.string.reminder_explanation else R.string.reminder_imported_explanation)
                    lembrar.isEnabled = editavel
                    lembrar.setText(if (state.automatico) R.string.reminder_option else R.string.reminder_legacy_option)
                    automaticos.visibility = if (state.automatico) View.GONE else View.VISIBLE
                    automaticos.isEnabled = editavel
                    escolherLembrete.isEnabled = editavel && state.lembrar
                    escolherLembrete.visibility = if (state.lembrar && !state.automatico) View.VISIBLE else View.GONE
                    permissao.visibility = if (state.lembrar) View.VISIBLE else View.GONE
                    statusPermissao.visibility = if (state.lembrar) View.VISIBLE else View.GONE
                    relogioLembrete.visibility = if (state.lembrar && !state.automatico) View.VISIBLE else View.GONE
                    relogioLembrete.text = getString(R.string.reminder_device_clock, referenciaRelogio())
                    escolherLembrete.text = state.lembrete?.let {
                        getString(R.string.reminder_time_format, Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")))
                    } ?: getString(R.string.reminder_choose_time)
                    if (titulo.text.toString() != state.titulo) titulo.setText(state.titulo)
                    if (descricao.text.toString() != state.descricao) descricao.setText(state.descricao)
                    val selecionada = disciplinas.indexOfFirst { it.id == state.disciplinaId }
                    disciplina.setText(rotulos.getOrNull(selecionada) ?: "", false)
                    tipo.setText(tiposTexto[tipos.indexOf(state.tipo)], false)
                    prioridade.setText(prioridadesTexto[prioridades.indexOf(state.prioridade)], false)
                    preenchendo = false
                    prazo.text = state.prazoExato?.let { getString(R.string.activity_imported_due,
                        com.everton.campusflow.prazos.Prazos.formatar(it)) } ?: state.prazo?.let { getString(R.string.activity_due_label,
                        LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                        ?: getString(R.string.activity_choose_due)
                    salvar.setText(if (state.ocupado) R.string.activity_saving else
                        if (model.editando) R.string.activity_update else R.string.activity_save)
                    val mensagem = state.erro ?: when {
                        !state.pronto -> R.string.activity_loading
                        !state.disciplinasCarregadas -> R.string.disciplines_loading
                        state.disciplinas.isEmpty() -> R.string.activity_no_disciplines
                        state.disciplinaId != null && selecionada < 0 -> R.string.activity_discipline_required
                        else -> null
                    }
                    status.visibility = if (mensagem == null) View.GONE else View.VISIBLE
                    if (mensagem != null) status.setText(mensagem)
                    if (state.finalizado) {
                        state.aviso?.let { Toast.makeText(this@AtividadeFormActivity, it, Toast.LENGTH_LONG).show() }
                        finish()
                    }
                }
            }
        }
    }
}
