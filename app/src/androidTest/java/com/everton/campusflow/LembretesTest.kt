package com.everton.campusflow

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LembretesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun migracao2paraAtualPreservaAtividadeEDisciplina() = verificarMigracao(2)
    @Test fun migracao3para4PreservaLembreteLegadoSemAtivarAutomatico() = verificarMigracao(3)
    @Test fun migracao4para5PreservaAutomaticosEHistorico() = verificarMigracao(4)
    private fun verificarMigracao(origem: Int) = runBlocking<Unit> {
        val name = "migration-lembretes-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.everton.campusflow.data.CampusFlowDatabase/$origem.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                    .getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO disciplinas(id,nome,professor) VALUES(9,'Mobile','Ana')")
            old.execSQL("INSERT INTO atividades(id,titulo,disciplinaId,tipo,prazoEpochDay,prioridade,concluida,descricao) VALUES(13,'Prova',9,'PROVA',20760,'ALTA',1,'Preservar')")
            if (origem >= 3) old.execSQL("UPDATE atividades SET lembreteEpochMillis = 1800000000000, lembreteEnviado = 1 WHERE id = 13")
            if (origem == 4) {
                old.execSQL("UPDATE atividades SET lembretesAutomaticos=1, avisoAtrasoDia=20761, lembretesAtivadosEm=1700000000000 WHERE id=13")
                old.execSQL("INSERT INTO entregas_automaticas(atividadeId,prazo,etapa) VALUES(13,20760,'VESPERA')")
            }
            old.version = origem
        }
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name)
            .addMigrations(CampusFlowDatabase.MIGRATION_2_3, CampusFlowDatabase.MIGRATION_3_4, CampusFlowDatabase.MIGRATION_4_5, CampusFlowDatabase.MIGRATION_5_6, CampusFlowDatabase.MIGRATION_6_7).build()
        try {
            val atividade = db.atividadeDao().buscarPorId(13)!!
            assertEquals("Prova", atividade.titulo)
            assertEquals("Preservar", atividade.descricao)
            assertEquals(9L, atividade.disciplinaId)
            assertTrue(atividade.concluida)
            if (origem >= 3) {
                assertEquals(1800000000000L, atividade.lembreteEpochMillis)
                assertTrue(atividade.lembreteEnviado)
            } else {
                assertNull(atividade.lembreteEpochMillis)
                assertFalse(atividade.lembreteEnviado)
            }
            if (origem == 4) {
                assertTrue(atividade.lembretesAutomaticos)
                assertEquals(20761L, atividade.avisoAtrasoDia)
                assertEquals(1700000000000L, atividade.lembretesAtivadosEm)
                assertTrue(db.atividadeDao().etapaEntregue(13, 20760, "VESPERA"))
            } else {
                assertFalse(atividade.lembretesAutomaticos)
                assertNull(atividade.avisoAtrasoDia)
                assertNull(atividade.lembretesAtivadosEm)
            }
            assertNull(atividade.prazoExatoEpochMillis)
            assertEquals(Disciplina(9, "Mobile", "Ana"), db.disciplinaDao().buscarPorId(9))
            assertEquals(7, db.openHelper.readableDatabase.version)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun entregaValidaTokenHorarioPermissaoStatusEDuplicacao() = runBlocking<Unit> {
        val name = "entregador-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
        try {
            val disciplina = db.disciplinaDao().inserir(Disciplina(nome = "Teste"))
            val agora = 1_800_000_000_000L
            val base = Atividade(titulo = "Lembrete", disciplinaId = disciplina, tipo = TipoAtividade.PROVA,
                prioridade = PrioridadeAtividade.MEDIA, prazoEpochDay = LocalDate.now().toEpochDay(),
                lembreteEpochMillis = agora - 1_000)
            val id = db.atividadeDao().inserir(base)
            var permitida = false
            var falhar = false
            val enviadas = mutableListOf<Long>()
            val publicador = object : PublicadorLembrete {
                override fun habilitado() = permitida
                override fun publicar(atividade: Atividade) {
                    if (falhar) throw IllegalStateException("Falha simulada antes de publicar")
                    enviadas += atividade.id
                }
            }
            val engine = EntregadorLembrete(db, publicador)
            val horario = base.lembreteEpochMillis!!
            assertEquals(ResultadoLembrete.IGNORADO, engine.entregar(id, horario + 1, agora))
            assertEquals(ResultadoLembrete.ANTECIPADO, engine.entregar(id, horario, horario - 1))
            assertEquals(ResultadoLembrete.BLOQUEADO, engine.entregar(id, horario, agora))
            assertFalse(db.atividadeDao().buscarPorId(id)!!.lembreteEnviado)
            permitida = true
            falhar = true
            try { engine.entregar(id, horario, agora); fail("Deve propagar falha para o Worker repetir")
            } catch (_: IllegalStateException) { /* não marca entregue em falha */ }
            assertFalse(db.atividadeDao().buscarPorId(id)!!.lembreteEnviado)
            falhar = false
            assertEquals(ResultadoLembrete.ENVIADO, engine.entregar(id, horario, agora))
            assertEquals(ResultadoLembrete.IGNORADO, engine.entregar(id, horario, agora))
            assertEquals(listOf(id), enviadas)
            db.atividadeDao().atualizar(base.copy(id = id, concluida = true))
            assertEquals(ResultadoLembrete.IGNORADO, engine.entregar(id, horario, agora))
            db.atividadeDao().atualizar(base.copy(id = id, lembreteEpochMillis = horario + 500))
            assertEquals(ResultadoLembrete.IGNORADO, engine.entregar(id, horario, agora))
            db.atividadeDao().atualizar(base.copy(id = id, lembreteEpochMillis = null))
            assertEquals(ResultadoLembrete.IGNORADO, engine.entregar(id, horario, agora))
            db.atividadeDao().excluirPorId(id)
            assertEquals(ResultadoLembrete.IGNORADO, engine.entregar(id, horario, agora))
            assertEquals(1, enviadas.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
