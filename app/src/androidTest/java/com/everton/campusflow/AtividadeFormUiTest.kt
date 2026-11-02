package com.everton.campusflow

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matcher
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtividadeFormUiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = CampusFlowDatabase.getInstance(context)
    private fun abrir(id: Long? = null): ActivityScenario<AtividadeFormActivity> {
        val intent = Intent(context, AtividadeFormActivity::class.java)
        if (id != null) intent.putExtra(AtividadeFormActivity.EXTRA_ATIVIDADE_ID, id)
        return ActivityScenario.launch(intent)
    }
    private suspend fun aguardar(scenario: ActivityScenario<AtividadeFormActivity>,
        condicao: (AtividadeFormActivity) -> Boolean) = withTimeout(10_000) {
        while (true) {
            var pronto = false
            scenario.onActivity { pronto = condicao(it) }
            if (pronto) return@withTimeout
            delay(30)
        }
    }

    @Test fun criarRestauraRascunhoEditarPreservaConclusaoEExcluirConfirma() = runBlocking<Unit> {
        val tag = "teste-form-${UUID.randomUUID()}"
        val disciplinaId = db.disciplinaDao().inserir(Disciplina(nome = tag, professor = "Ana"))
        var atividadeId: Long? = null
        val ontem = LocalDate.now().minusDays(1)
        try {
            abrir().use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnSalvarAtividade).isEnabled }
                scenario.onActivity {
                    it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    it.findViewById<Button>(R.id.btnSalvarAtividade).performClick()
                    assertNotNull(it.findViewById<TextInputLayout>(R.id.inputTituloAtividade).error)
                    it.findViewById<TextInputEditText>(R.id.edtTituloAtividade).setText("  $tag  ")
                    val seletor = it.findViewById<MaterialAutoCompleteTextView>(R.id.seletorDisciplina)
                    seletor.showDropDown()
                }
                onData(equalTo(context.getString(R.string.activity_discipline_option, tag, "Ana")))
                    .inRoot(isPlatformPopup()).perform(click())
                aguardar(scenario) {
                    ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.disciplinaId == disciplinaId
                }
                scenario.onActivity {
                    it.findViewById<Button>(R.id.btnPrazoAtividade).performClick()
                }
                onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
                    override fun getDescription() = "Selecionar prazo passado no calendário"
                    override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
                    override fun perform(uiController: UiController, view: View) {
                        (view as DatePicker).updateDate(ontem.year, ontem.monthValue - 1, ontem.dayOfMonth)
                    }
                })
                onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
                scenario.recreate()
                aguardar(scenario) {
                    ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.disciplinasCarregadas
                }
                scenario.onActivity {
                    val state = ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value
                    assertEquals("  $tag  ", state.titulo)
                    assertEquals(disciplinaId, state.disciplinaId)
                    assertEquals(ontem.toEpochDay(), state.prazo)
                    assertEquals(TipoAtividade.TAREFA, state.tipo)
                    assertEquals(PrioridadeAtividade.MEDIA, state.prioridade)
                    it.findViewById<Button>(R.id.btnSalvarAtividade).performClick()
                }
                atividadeId = withTimeout(5_000) { db.atividadeDao().observarTodasComDisciplina().first {
                    rows -> rows.any { it.titulo == tag }
                } }.first { it.titulo == tag }.id
            }
            val id = atividadeId!!
            assertEquals("", db.atividadeDao().buscarPorId(id)?.descricao)
            assertEquals(false, db.atividadeDao().buscarPorId(id)?.concluida)
            abrir(id).use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnSalvarAtividade).isEnabled }
                // A edição deve preservar uma conclusão gravada depois da abertura do formulário.
                db.atividadeDao().atualizarConcluida(id, true)
                scenario.onActivity {
                    it.findViewById<TextInputEditText>(R.id.edtTituloAtividade).setText("$tag editada")
                    it.findViewById<Button>(R.id.btnSalvarAtividade).performClick()
                }
                withTimeout(5_000) { while (db.atividadeDao().buscarPorId(id)?.titulo != "$tag editada") delay(25) }
                assertEquals(true, db.atividadeDao().buscarPorId(id)?.concluida)
            }
            abrir(id).use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnExcluirAtividade).isEnabled }
                scenario.onActivity { it.findViewById<Button>(R.id.btnExcluirAtividade).performClick() }
                onView(withText(R.string.discipline_delete_cancel)).inRoot(isDialog()).perform(click())
                assertNotNull(db.atividadeDao().buscarPorId(id))
                scenario.onActivity { it.findViewById<Button>(R.id.btnExcluirAtividade).performClick() }
                onView(withText(R.string.discipline_delete_confirm)).inRoot(isDialog()).perform(click())
                withTimeout(5_000) { while (db.atividadeDao().buscarPorId(id) != null) delay(25) }
            }
        } finally {
            // Somente registros deste teste, inclusive se a asserção falhar depois da inserção.
            db.atividadeDao().observarTodasComDisciplina().first().filter { it.disciplinaId == disciplinaId }
                .forEach { db.atividadeDao().excluirPorId(it.id) }
            db.disciplinaDao().excluirPorId(disciplinaId)
        }
    }

    @Test fun idInexistenteEDisciplinaRemovidaImpedemSalvar() = runBlocking<Unit> {
        abrir(Long.MAX_VALUE).use { scenario ->
            aguardar(scenario) { ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.inexistente }
            scenario.onActivity { assertFalse(it.findViewById<Button>(R.id.btnSalvarAtividade).isEnabled) }
        }
        val tag = "teste-vinculo-form-${UUID.randomUUID()}"
        val id = db.disciplinaDao().inserir(Disciplina(nome = tag))
        try {
            abrir().use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnSalvarAtividade).isEnabled }
                scenario.onActivity { ViewModelProvider(it)[AtividadeFormViewModel::class.java].rascunho(
                    titulo = tag, disciplinaId = id, prazo = LocalDate.now().toEpochDay()) }
                db.disciplinaDao().excluirPorId(id)
                scenario.onActivity { ViewModelProvider(it)[AtividadeFormViewModel::class.java].salvar() }
                aguardar(scenario) { ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.erro ==
                    R.string.activity_discipline_required }
                assertFalse(db.atividadeDao().observarTodasComDisciplina().first().any { it.titulo == tag })
            }
        } finally { db.disciplinaDao().excluirPorId(id) }
    }
}
