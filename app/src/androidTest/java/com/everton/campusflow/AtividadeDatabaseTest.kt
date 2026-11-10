package com.everton.campusflow

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtividadeDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun atividade(disciplinaId: Long) = Atividade(
        titulo = "Trabalho final", disciplinaId = disciplinaId, tipo = TipoAtividade.TRABALHO,
        prazoEpochDay = LocalDate.of(2026, 10, 30).toEpochDay(),
        prioridade = PrioridadeAtividade.ALTA, descricao = "Apresentação",
    )

    @Test fun migracaoPreservaDisciplinasEValidaNovaTabela() = runBlocking<Unit> {
        val name = "migration-atividades-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        // Recria a versão anterior a partir do esquema realmente versionado, não do modelo v2.
        val schemaText = instrumentation.context.assets.open(
            "com.everton.campusflow.data.CampusFlowDatabase/1.json",
        ).bufferedReader().use { it.readText() }
        val schema = JSONObject(schemaText).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO disciplinas(id,nome,professor) VALUES(5,'Mobile','Ana'),(7,'Mobile','')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name)
            .addMigrations(CampusFlowDatabase.MIGRATION_1_2, CampusFlowDatabase.MIGRATION_2_3, CampusFlowDatabase.MIGRATION_3_4, CampusFlowDatabase.MIGRATION_4_5, CampusFlowDatabase.MIGRATION_5_6, CampusFlowDatabase.MIGRATION_6_7).build()
        try {
            assertEquals(
                listOf(Disciplina(5, "Mobile", "Ana"), Disciplina(7, "Mobile", "")),
                db.disciplinaDao().observarTodas().first(),
            )
            assertEquals(7, db.openHelper.readableDatabase.version)
            val id = db.atividadeDao().inserir(atividade(5))
            assertEquals(atividade(5).copy(id = id), db.atividadeDao().buscarPorId(id))
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_atividades_disciplinaId'",
            ).use { assertEquals(1, it.count) }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun chaveEstrangeiraEExclusaoProtegemVinculos() = runBlocking<Unit> {
        val name = "fk-atividades-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
        try {
            val disciplinas = db.disciplinaDao()
            val atividades = db.atividadeDao()
            val id = disciplinas.inserir(Disciplina(nome = "Mobile"))
            val outra = disciplinas.inserir(Disciplina(nome = "Sem atividades"))
            val atividadeId = atividades.inserir(atividade(id))
            assertEquals(ResultadoExclusaoDisciplina.COM_ATIVIDADES, disciplinas.excluirSeSemAtividades(id))
            assertNotNull(disciplinas.buscarPorId(id))
            try {
                disciplinas.excluirPorId(id)
                fail("A FK deve impedir exclusão direta da disciplina vinculada")
            } catch (_: SQLiteConstraintException) { /* proteção no próprio banco */ }
            try {
                atividades.inserir(atividade(Long.MAX_VALUE))
                fail("A FK deve recusar uma disciplina inexistente")
            } catch (_: SQLiteConstraintException) { /* esperado */ }
            try {
                atividades.atualizar(atividade(Long.MAX_VALUE).copy(id = atividadeId))
                fail("A FK também deve impedir vínculo inválido em uma atualização")
            } catch (_: SQLiteConstraintException) { /* esperado */ }
            assertEquals(id, atividades.buscarPorId(atividadeId)?.disciplinaId)
            assertEquals(ResultadoExclusaoDisciplina.EXCLUIDA, disciplinas.excluirSeSemAtividades(outra))
            assertEquals(ResultadoExclusaoDisciplina.INEXISTENTE, disciplinas.excluirSeSemAtividades(outra))
            assertEquals(1, atividades.excluirPorId(atividadeId))
            assertEquals(ResultadoExclusaoDisciplina.EXCLUIDA, disciplinas.excluirSeSemAtividades(id))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
