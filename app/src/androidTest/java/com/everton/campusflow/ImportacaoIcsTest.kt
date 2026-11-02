package com.everton.campusflow

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.importacao.*
import com.everton.campusflow.lembretes.Lembretes
import java.time.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportacaoIcsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun importarEReimportarPreservaEdicaoConclusaoEImpedeLoteParcial() = runBlocking<Unit> {
        val name = "importacao-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
        try {
            val disciplina = db.disciplinaDao().inserir(Disciplina(nome = "Teste"))
            val evento = EventoIcs("uid", "Importada", "Descrição", LocalDate.now().toEpochDay(),
                System.currentTimeMillis() + 86_400_000, "UTC")
            val servico = ImportadorIcs(db)
            val resultado = servico.importar(listOf(EscolhaIcs(evento, disciplina)), false, Instant.now(), ZoneId.of("UTC"))
            val id = resultado.ids.single()
            val salvo = db.atividadeDao().buscarPorId(id)!!
            assertEquals(evento.instante, salvo.prazoExatoEpochMillis)
            assertFalse(salvo.lembretesAutomaticos)
            db.atividadeDao().atualizar(salvo.copy(titulo="Editado", concluida=true))
            val repetido = servico.importar(listOf(EscolhaIcs(evento.copy(titulo="Novo remoto"), disciplina)), true, Instant.now(), ZoneId.of("UTC"))
            assertEquals(1, repetido.repetidos); assertTrue(repetido.ids.isEmpty())
            val atual = db.atividadeDao().buscarPorId(id)!!
            assertEquals("Editado", atual.titulo); assertTrue(atual.concluida); assertFalse(atual.lembretesAutomaticos)
            try {
                servico.importar(listOf(EscolhaIcs(evento.copy(uid="novo"), disciplina), EscolhaIcs(evento.copy(uid="erro"), Long.MAX_VALUE)), false, Instant.now(), ZoneId.of("UTC"))
                fail("Deve rejeitar todo o lote")
            } catch (_: IllegalArgumentException) { }
            assertEquals(1, db.atividadeDao().buscarTodas().size)
            db.atividadeDao().excluirPorId(id)
            assertNull(db.atividadeDao().buscarImportado(ImportadorIcs.ORIGEM, "uid"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun previaNaoGravaRestauraSelecaoERespeitaConfirmacao() = runBlocking<Unit> {
        val db = CampusFlowDatabase.getInstance(context)
        val tag = "ics-ui-${UUID.randomUUID()}"
        val disciplina = db.disciplinaDao().inserir(Disciplina(nome=tag))
        val arquivo = File(context.cacheDir, "$tag.ics")
        val conteudo = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:$tag\r\nSUMMARY:$tag\r\nDTSTART:20271110T210000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        arquivo.writeText(conteudo)
        try {
            ActivityScenario.launch<ImportarCalendarioActivity>(Intent(context, ImportarCalendarioActivity::class.java)).use { scenario ->
                suspend fun aguardar(condicao: (ImportarState) -> Boolean) = withTimeout(10_000) {
                    while (true) {
                        var pronto = false
                        scenario.onActivity { pronto = condicao(ViewModelProvider(it)[ImportarCalendarioViewModel::class.java].estado.value) }
                        if (pronto) break
                        delay(30)
                    }
                }
                scenario.onActivity { ViewModelProvider(it)[ImportarCalendarioViewModel::class.java].carregar(Uri.fromFile(arquivo)) }
                aguardar { !it.ocupado && it.eventos.size == 1 && it.disciplinas.any { d -> d.id == disciplina } }
                assertNull(db.atividadeDao().buscarImportado(ImportadorIcs.ORIGEM, tag))
                scenario.onActivity { ViewModelProvider(it)[ImportarCalendarioViewModel::class.java].selecionar(tag, disciplina) }
                scenario.recreate()
                aguardar { !it.ocupado && it.selecoes[tag] == disciplina }
                arquivo.writeText("arquivo inválido")
                scenario.onActivity { ViewModelProvider(it)[ImportarCalendarioViewModel::class.java].carregar(Uri.fromFile(arquivo)) }
                aguardar { !it.ocupado && it.mensagem == R.string.import_read_error }
                scenario.onActivity {
                    assertTrue(ViewModelProvider(it)[ImportarCalendarioViewModel::class.java].estado.value.eventos.isEmpty())
                    assertFalse(it.findViewById<android.view.View>(R.id.btnImportarEventos).isEnabled)
                }
                arquivo.writeText(conteudo)
                scenario.onActivity { ViewModelProvider(it)[ImportarCalendarioViewModel::class.java].carregar(Uri.fromFile(arquivo)) }
                aguardar { !it.ocupado && it.eventos.size == 1 }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[ImportarCalendarioViewModel::class.java]
                    assertFalse(model.estado.value.lembrar)
                    assertTrue(model.estado.value.selecoes.isEmpty())
                    model.selecionar(tag, disciplina)
                }
                aguardar { it.selecoes[tag] == disciplina }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                // Captura da prévia para inspeção visual, sem gravar dados pessoais em logs.
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.cacheDir, "previa-ics-teste.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                scenario.onActivity {
                    it.findViewById<android.view.View>(R.id.btnImportarEventos).performClick()
                }
                aguardar { !it.ocupado && it.resultado?.ids?.size == 1 }
                val id = db.atividadeDao().buscarImportado(ImportadorIcs.ORIGEM, tag)!!
                assertEquals(Instant.parse("2027-11-10T21:00:00Z").toEpochMilli(), db.atividadeDao().buscarPorId(id)!!.prazoExatoEpochMillis)
            }
        } finally {
            db.atividadeDao().buscarTodas().filter { it.disciplinaId == disciplina }.forEach {
                db.atividadeDao().excluirPorId(it.id); Lembretes.sincronizar(context, it.id)
            }
            db.disciplinaDao().excluirPorId(disciplina); arquivo.delete()
        }
    }
}
