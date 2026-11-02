package com.everton.campusflow

import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticosTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun editarLegadoPreservaHorarioEConversaoExigeEscolhaExplicita() = runBlocking<Unit> {
        val db = CampusFlowDatabase.getInstance(context)
        val dao = db.atividadeDao()
        val disciplina = db.disciplinaDao().inserir(Disciplina(nome = "legado-${UUID.randomUUID()}"))
        val horario = System.currentTimeMillis() + 86_400_000L
        val id = dao.inserir(Atividade(titulo = "Legado", disciplinaId = disciplina,
            tipo = TipoAtividade.TAREFA, prioridade = PrioridadeAtividade.MEDIA,
            prazoEpochDay = LocalDate.now().plusDays(4).toEpochDay(), lembreteEpochMillis = horario))
        try {
            suspend fun editar(converter: Boolean) {
                val intent = Intent(context, AtividadeFormActivity::class.java)
                    .putExtra(AtividadeFormActivity.EXTRA_ATIVIDADE_ID, id)
                ActivityScenario.launch<AtividadeFormActivity>(intent).use { scenario ->
                    withTimeout(10_000) { while (true) {
                        var pronto = false
                        scenario.onActivity { pronto = ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.disciplinasCarregadas }
                        if (pronto) break
                        delay(25)
                    } }
                    scenario.onActivity {
                        val model = ViewModelProvider(it)[AtividadeFormViewModel::class.java]
                        assertFalse(model.estado.value.automatico)
                        assertTrue(model.estado.value.lembrar)
                        if (converter) it.findViewById<View>(R.id.btnUsarAutomaticos).performClick()
                        model.rascunho(titulo = if (converter) "Convertida" else "Editada")
                        model.salvar()
                    }
                    withTimeout(10_000) { while (dao.buscarPorId(id)?.titulo != if (converter) "Convertida" else "Editada") delay(25) }
                }
            }
            editar(false)
            assertEquals(horario, dao.buscarPorId(id)!!.lembreteEpochMillis)
            assertFalse(dao.buscarPorId(id)!!.lembretesAutomaticos)
            editar(true)
            assertNull(dao.buscarPorId(id)!!.lembreteEpochMillis)
            assertTrue(dao.buscarPorId(id)!!.lembretesAutomaticos)
        } finally {
            dao.excluirPorId(id)
            Lembretes.sincronizar(context, id)
            db.disciplinaDao().excluirPorId(disciplina)
        }
    }

    @Test fun duasEtapasPermissaoReaberturaMudancaDePrazoEExclusao() = runBlocking<Unit> {
        val name = "automaticos-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
        try {
            val disciplina = db.disciplinaDao().inserir(Disciplina(nome = "Isolada"))
            val fuso = ZoneId.of("America/Sao_Paulo")
            val prazo = LocalDate.of(2026, 11, 10).toEpochDay()
            val base = Atividade(titulo = "Teste", disciplinaId = disciplina, tipo = TipoAtividade.TAREFA,
                prazoEpochDay = prazo, prioridade = PrioridadeAtividade.MEDIA, lembretesAutomaticos = true,
                avisoAtrasoDia = prazo + 1)
            val id = db.atividadeDao().inserir(base)
            val dao = db.atividadeDao()
            var permitido = false
            val entregas = mutableListOf<EtapaAutomatica>()
            val publicador = object : PublicadorLembrete {
                override fun habilitado() = permitido
                override fun publicar(atividade: Atividade) = error("Não deve usar texto legado")
                override fun publicarAutomatico(atividade: Atividade, etapa: EtapaAutomatica) { entregas += etapa }
            }
            val engine = EntregadorAutomatico(db, publicador)
            suspend fun entregar(etapa: EtapaAutomatica, dia: Long = prazo, horario: Long = PlanoAutomatico.horario(base, etapa, fuso)) =
                engine.entregar(id, dia, etapa, horario, Instant.ofEpochMilli(horario), fuso)
            assertEquals(ResultadoLembrete.BLOQUEADO, entregar(EtapaAutomatica.VESPERA))
            permitido = true
            assertEquals(ResultadoLembrete.ENVIADO, entregar(EtapaAutomatica.VESPERA))
            assertEquals(ResultadoLembrete.IGNORADO, entregar(EtapaAutomatica.VESPERA))
            dao.atualizarConcluida(id, true)
            assertEquals(ResultadoLembrete.IGNORADO, entregar(EtapaAutomatica.ATRASO))
            dao.atualizarConcluida(id, false)
            assertEquals(ResultadoLembrete.IGNORADO, entregar(EtapaAutomatica.VESPERA))
            assertEquals(ResultadoLembrete.ENVIADO, entregar(EtapaAutomatica.ATRASO))
            assertEquals(ResultadoLembrete.IGNORADO, entregar(EtapaAutomatica.ATRASO))
            dao.atualizar(base.copy(id = id, prazoEpochDay = prazo + 7, avisoAtrasoDia = prazo + 8))
            assertEquals(ResultadoLembrete.IGNORADO, entregar(EtapaAutomatica.ATRASO))
            val nova = dao.buscarPorId(id)!!
            val horaNova = PlanoAutomatico.horario(nova, EtapaAutomatica.VESPERA, fuso)
            assertEquals(ResultadoLembrete.ENVIADO, entregar(EtapaAutomatica.VESPERA, prazo + 7, horaNova))
            assertEquals(listOf(EtapaAutomatica.VESPERA, EtapaAutomatica.ATRASO, EtapaAutomatica.VESPERA), entregas)
            dao.excluirPorId(id)
            assertFalse(dao.etapaEntregue(id, prazo, EtapaAutomatica.ATRASO.name))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun formularioNovoSemSeletorGravaDoisTrabalhosECancelaAoConcluir() = runBlocking<Unit> {
        val db = CampusFlowDatabase.getInstance(context)
        val dao = db.atividadeDao()
        val tag = "auto-ui-${UUID.randomUUID()}"
        val disciplina = db.disciplinaDao().inserir(Disciplina(nome = tag))
        var id: Long? = null
        try {
            ActivityScenario.launch<AtividadeFormActivity>(Intent(context, AtividadeFormActivity::class.java)).use { scenario ->
                withTimeout(10_000) {
                    while (true) {
                        var pronto = false
                        scenario.onActivity { pronto = ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.disciplinasCarregadas }
                        if (pronto) break
                        delay(25)
                    }
                }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[AtividadeFormViewModel::class.java]
                    assertTrue(model.estado.value.automatico)
                    model.rascunho(titulo = tag, disciplinaId = disciplina,
                        prazo = LocalDate.now().plusDays(5).toEpochDay(), lembrar = true)
                }
                scenario.recreate()
                withTimeout(10_000) {
                    while (true) {
                        var pronto = false
                        scenario.onActivity { pronto = ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.disciplinasCarregadas }
                        if (pronto) break
                        delay(25)
                    }
                }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[AtividadeFormViewModel::class.java]
                    assertTrue(model.estado.value.lembrar)
                    assertEquals(View.GONE, it.findViewById<View>(R.id.btnHorarioLembrete).visibility)
                    model.salvar()
                }
                withTimeout(10_000) { while (true) {
                    id = dao.buscarTodas().find { it.titulo == tag }?.id
                    if (id != null) break
                    delay(25)
                } }
            }
            val atividadeId = id!!
            val work = WorkManager.getInstance(context)
            Lembretes.sincronizar(context, atividadeId)
            val salvo = dao.buscarPorId(atividadeId)!!
            assertTrue(salvo.lembretesAutomaticos)
            assertNull(salvo.lembreteEpochMillis)
            for (etapa in EtapaAutomatica.entries) {
                val nome = "automatico-$atividadeId-${etapa.name}"
                val primeiro = work.getWorkInfosForUniqueWork(nome).get().single { !it.state.isFinished }.id
                Lembretes.sincronizar(context, atividadeId)
                assertEquals(primeiro, work.getWorkInfosForUniqueWork(nome).get().single { !it.state.isFinished }.id)
            }
            dao.atualizarConcluida(atividadeId, true)
            Lembretes.sincronizar(context, atividadeId)
            assertTrue(work.getWorkInfosByTag("atividade-$atividadeId").get().all { it.state.isFinished })
        } finally {
            dao.buscarTodas().filter { it.disciplinaId == disciplina }.forEach {
                dao.excluirPorId(it.id); Lembretes.sincronizar(context, it.id)
            }
            db.disciplinaDao().excluirPorId(disciplina)
        }
    }
}
