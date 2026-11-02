package com.everton.campusflow

import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
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
class DashboardAtividadesTest {
    @Test fun contagensReagemAoBancoEAtrasadasSaoParteDasPendentes() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = CampusFlowDatabase.getInstance(context)
        val dao = db.atividadeDao()
        val hoje = LocalDate.now()
        val anteriores = dao.observarTodasComDisciplina().first()
        val p = anteriores.count { !it.concluida }
        val c = anteriores.count { it.concluida }
        val a = anteriores.count { it.estaAtrasada(hoje) }
        val disciplinaId = db.disciplinaDao().inserir(Disciplina(nome = "teste-dashboard-${UUID.randomUUID()}"))
        val ids = mutableListOf<Long>()
        try {
            val modelo = Atividade(titulo = "Teste de contagens", disciplinaId = disciplinaId,
                tipo = TipoAtividade.TAREFA, prioridade = PrioridadeAtividade.MEDIA,
                prazoEpochDay = hoje.toEpochDay())
            ids += dao.inserir(modelo.copy(prazoEpochDay = hoje.minusDays(1).toEpochDay()))
            ids += dao.inserir(modelo)
            ids += dao.inserir(modelo.copy(concluida = true, prazoEpochDay = hoje.minusDays(1).toEpochDay()))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                suspend fun conferir(pendentes: Int, concluidas: Int, atrasadas: Int) {
                    withTimeout(8_000) {
                        while (true) {
                            var correto = false
                            scenario.onActivity {
                                correto = it.findViewById<TextView>(R.id.txtPendentes).text.toString() ==
                                    context.getString(R.string.dashboard_pending_count, pendentes) &&
                                    it.findViewById<TextView>(R.id.txtConcluidas).text.toString() ==
                                    context.getString(R.string.dashboard_completed_count, concluidas) &&
                                    it.findViewById<TextView>(R.id.txtAtrasadas).text.toString() ==
                                    context.getString(R.string.dashboard_overdue_count, atrasadas)
                            }
                            if (correto) return@withTimeout
                            delay(30)
                        }
                    }
                }
                conferir(p + 2, c + 1, a + 1)
                dao.atualizarConcluida(ids[0], true)
                conferir(p + 1, c + 2, a)
                scenario.recreate()
                conferir(p + 1, c + 2, a)
                scenario.onActivity {
                    assertEquals(context.getString(R.string.dashboard_has_activities),
                        it.findViewById<TextView>(R.id.txtTituloResumoAtividades).text.toString())
                }
            }
        } finally {
            ids.forEach { dao.excluirPorId(it) }
            db.disciplinaDao().excluirPorId(disciplinaId)
        }
    }
}
