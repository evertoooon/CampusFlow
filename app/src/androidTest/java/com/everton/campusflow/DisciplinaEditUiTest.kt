package com.everton.campusflow

import android.content.Intent
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.everton.campusflow.data.CampusFlowDatabase
import com.everton.campusflow.data.Disciplina
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisciplinaEditUiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = CampusFlowDatabase.getInstance(context)
    private val dao = db.disciplinaDao()

    private fun abrir(id: Long): ActivityScenario<DisciplinaFormActivity> =
        ActivityScenario.launch(
            Intent(context, DisciplinaFormActivity::class.java)
                .putExtra(DisciplinaFormActivity.EXTRA_DISCIPLINA_ID, id),
        )

    private suspend fun aguardar(
        scenario: ActivityScenario<DisciplinaFormActivity>,
        condicao: (DisciplinaFormActivity) -> Boolean,
    ) {
        withTimeout(8_000) {
            while (true) {
                var pronto = false
                scenario.onActivity { pronto = condicao(it) }
                if (pronto) return@withTimeout
                delay(25)
            }
        }
    }

    @Test
    fun editarPreservaRascunhoAoRecriarEAtualizaMesmoId() = runBlocking<Unit> {
        val tag = "teste-edicao-${UUID.randomUUID()}"
        val id = dao.inserir(Disciplina(nome = tag, professor = "Original"))
        try {
            val total = withTimeout(5_000) { dao.observarTodas().first() }.size
            abrir(id).use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnSalvarDisciplina).isEnabled }
                scenario.onActivity {
                    assertEquals(context.getString(R.string.discipline_edit_title),
                        it.findViewById<TextView>(R.id.txtTituloDisciplina).text.toString())
                    assertEquals(tag, it.findViewById<TextInputEditText>(R.id.edtNomeDisciplina).text.toString())
                    assertEquals("Original", it.findViewById<TextInputEditText>(R.id.edtProfessorDisciplina).text.toString())
                    it.findViewById<TextInputEditText>(R.id.edtNomeDisciplina).setText("$tag editada")
                    it.findViewById<TextInputEditText>(R.id.edtProfessorDisciplina).setText("Rascunho")
                }
                // Exercita a mesma recriação de Activity que ocorre em mudanças de configuração.
                scenario.recreate()
                aguardar(scenario) {
                    it.findViewById<TextInputEditText>(R.id.edtNomeDisciplina).text.toString() == "$tag editada"
                }
                scenario.onActivity {
                    assertEquals("Rascunho", it.findViewById<TextInputEditText>(R.id.edtProfessorDisciplina).text.toString())
                    it.findViewById<Button>(R.id.btnSalvarDisciplina).performClick()
                }
                withTimeout(5_000) {
                    while (dao.buscarPorId(id)?.nome != "$tag editada") delay(25)
                }
            }
            assertEquals("Rascunho", dao.buscarPorId(id)?.professor)
            assertEquals(total, withTimeout(5_000) { dao.observarTodas().first() }.size)
        } finally {
            removerSomenteFixture(id, tag)
        }
    }

    @Test
    fun voltarNaoGravaEIdInexistenteBloqueiaSalvar() = runBlocking<Unit> {
        val tag = "teste-cancelar-${UUID.randomUUID()}"
        val id = dao.inserir(Disciplina(nome = tag))
        try {
            abrir(id).use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnSalvarDisciplina).isEnabled }
                scenario.onActivity {
                    it.findViewById<TextInputEditText>(R.id.edtNomeDisciplina).setText("$tag alterada")
                    it.findViewById<Button>(R.id.btnVoltarDisciplinas).performClick()
                }
            }
            assertEquals(tag, dao.buscarPorId(id)?.nome)
            abrir(Long.MAX_VALUE).use { scenario ->
                aguardar(scenario) {
                    it.findViewById<TextView>(R.id.txtErroSalvar).text.toString() ==
                        context.getString(R.string.discipline_not_found)
                }
                scenario.onActivity {
                    assertFalse(it.findViewById<Button>(R.id.btnSalvarDisciplina).isEnabled)
                }
            }
        } finally {
            removerSomenteFixture(id, tag)
        }
    }

    private fun removerSomenteFixture(id: Long, tag: String) {
        // Somente os registros exclusivos deste teste; nenhuma disciplina do usuário é apagada.
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM disciplinas WHERE id = ? OR nome IN (?, ?)",
            arrayOf<Any>(id, tag, "$tag editada"),
        )
    }

    @Test
    fun cancelarExclusaoPreservaEConfirmarRemoveSomenteIdSelecionado() = runBlocking<Unit> {
        val tag = "teste-exclusao-${UUID.randomUUID()}"
        val id = dao.inserir(Disciplina(nome = tag))
        val outroId = dao.inserir(Disciplina(nome = "$tag outra", professor = "Preservar"))
        try {
            abrir(id).use { scenario ->
                aguardar(scenario) { it.findViewById<Button>(R.id.btnExcluirDisciplina).isEnabled }
                scenario.onActivity {
                    it.findViewById<TextInputEditText>(R.id.edtNomeDisciplina).setText("$tag rascunho")
                    it.findViewById<Button>(R.id.btnExcluirDisciplina).performClick()
                }
                // A confirmação identifica o registro salvo, não uma edição ainda não gravada.
                onView(withText(context.getString(R.string.discipline_delete_confirmation, tag)))
                    .inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText(R.string.discipline_delete_cancel)).inRoot(isDialog()).perform(click())
                assertEquals(tag, dao.buscarPorId(id)?.nome)
                scenario.onActivity {
                    assertEquals("$tag rascunho", it.findViewById<TextInputEditText>(R.id.edtNomeDisciplina).text.toString())
                    it.findViewById<Button>(R.id.btnExcluirDisciplina).performClick()
                }
                onView(withText(R.string.discipline_delete_confirm)).inRoot(isDialog()).perform(click())
                withTimeout(5_000) {
                    while (dao.buscarPorId(id) != null) delay(25)
                }
            }
            assertEquals(Disciplina(id = outroId, nome = "$tag outra", professor = "Preservar"), dao.buscarPorId(outroId))
            assertEquals(0, dao.excluirPorId(id))
        } finally {
            removerSomenteFixture(id, tag)
            removerSomenteFixture(outroId, "$tag outra")
        }
    }
}
