package com.everton.campusflow

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.Lembretes
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LembreteFormTest {
    @Test fun validacaoRascunhoGravacaoReagendamentoECancelamento() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = CampusFlowDatabase.getInstance(context)
        val dao = db.atividadeDao()
        val tag = "teste-lembrete-${UUID.randomUUID()}"
        val disciplina = db.disciplinaDao().inserir(Disciplina(nome = tag))
        val futuro = System.currentTimeMillis() + 3_600_000
        var id: Long? = null
        try {
            ActivityScenario.launch<AtividadeFormActivity>(Intent(context, AtividadeFormActivity::class.java)).use { scenario ->
                suspend fun aguardar(check: (AtividadeFormState) -> Boolean) = withTimeout(10_000) {
                    while (true) {
                        var ok = false
                        scenario.onActivity { ok = check(ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value) }
                        if (ok) return@withTimeout
                        delay(30)
                    }
                }
                aguardar { it.pronto && it.disciplinasCarregadas }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[AtividadeFormViewModel::class.java]
                    model.rascunho(titulo = tag, disciplinaId = disciplina, prazo = LocalDate.now().toEpochDay(), lembrar = true, automatico = false)
                    model.salvar()
                    assertEquals(R.string.reminder_time_required, model.estado.value.erro)
                    model.rascunho(lembrete = System.currentTimeMillis() - 60_000)
                    model.salvar()
                    assertEquals(R.string.reminder_future_required, model.estado.value.erro)
                    model.rascunho(lembrete = futuro)
                }
                scenario.recreate()
                aguardar { it.pronto && it.disciplinasCarregadas }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[AtividadeFormViewModel::class.java]
                    assertTrue(model.estado.value.lembrar)
                    assertEquals(futuro, model.estado.value.lembrete)
                    model.salvar()
                }
                id = withTimeout(8_000) { dao.observarTodasComDisciplina().first { rows -> rows.any { it.titulo == tag } } }
                    .first { it.titulo == tag }.id
                withTimeout(8_000) {
                    while (WorkManager.getInstance(context).getWorkInfosForUniqueWork("lembrete-atividade-$id")
                            .get().none { !it.state.isFinished }) delay(30)
                }
            }
            val atividadeId = id!!
            val work = WorkManager.getInstance(context)
            assertEquals(futuro, dao.buscarPorId(atividadeId)?.lembreteEpochMillis)
            Lembretes.sincronizar(context, atividadeId)
            val primeiro = work.getWorkInfosForUniqueWork("lembrete-atividade-$atividadeId").get().single { !it.state.isFinished }
            Lembretes.sincronizar(context, atividadeId)
            assertEquals(primeiro.id, work.getWorkInfosForUniqueWork("lembrete-atividade-$atividadeId").get().single { !it.state.isFinished }.id)
            dao.atualizar(dao.buscarPorId(atividadeId)!!.copy(lembreteEpochMillis = futuro + 60_000))
            Lembretes.sincronizar(context, atividadeId)
            val segundo = work.getWorkInfosForUniqueWork("lembrete-atividade-$atividadeId").get().single { !it.state.isFinished }
            assertNotEquals(primeiro.id, segundo.id)
            assertTrue("horario-${futuro + 60_000}" in segundo.tags)
            dao.atualizarConcluida(atividadeId, true)
            Lembretes.sincronizar(context, atividadeId)
            assertTrue(work.getWorkInfosForUniqueWork("lembrete-atividade-$atividadeId").get().all { it.state.isFinished })
            dao.atualizarConcluida(atividadeId, false)
            Lembretes.sincronizar(context, atividadeId)
            assertTrue(work.getWorkInfosForUniqueWork("lembrete-atividade-$atividadeId").get().any { !it.state.isFinished })
            dao.excluirPorId(atividadeId)
            Lembretes.sincronizar(context, atividadeId)
            assertTrue(work.getWorkInfosForUniqueWork("lembrete-atividade-$atividadeId").get().all { it.state.isFinished })
        } finally {
            dao.buscarTodas().filter { it.disciplinaId == disciplina }.forEach {
                dao.excluirPorId(it.id); Lembretes.sincronizar(context, it.id)
            }
            id?.let { Lembretes.sincronizar(context, it) }
            db.disciplinaDao().excluirPorId(disciplina)
        }
    }
}
