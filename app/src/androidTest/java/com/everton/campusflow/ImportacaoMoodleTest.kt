package com.everton.campusflow

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImportacaoMoodleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Test fun repeticaoContasDistintasSemPrazoEEdicoesLocais() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, CampusFlowDatabase::class.java).build()
        try {
            val existente = db.disciplinaDao().inserir(Disciplina(nome="Mobile", professor="Professor local"))
            val previa = listOf(PreviaCursoMoodle(CursoMoodle(10,"Mobile remoto"), listOf(
                TarefaMoodle("Entrega", 1_800_000_000_000, 30), TarefaMoodle("Sem prazo", null, 31)), existente))
            val importer = ImportadorMoodle(db)
            val primeiro = importer.importar(7, previa)
            assertEquals(0, primeiro.disciplinasCriadas); assertEquals(1, primeiro.atividadesCriadas)
            val salvo = db.atividadeDao().buscarTodas().single()
            assertEquals(1_800_000_000_000, salvo.prazoExatoEpochMillis)
            assertFalse(salvo.lembretesAutomaticos)
            db.atividadeDao().atualizar(salvo.copy(titulo="Edicao local", concluida=true))
            val novamente = importer.importar(7, previa)
            assertEquals(1, novamente.repetidas); assertEquals(0, novamente.atividadesCriadas)
            assertEquals("Edicao local", db.atividadeDao().buscarTodas().single().titulo)
            assertTrue(db.atividadeDao().buscarTodas().single().concluida)
            assertEquals("Professor local", db.disciplinaDao().buscarPorId(existente)!!.professor)
            val outraConta = importer.importar(8, previa.map { it.copy(disciplinaId=null) })
            assertEquals(1, outraConta.disciplinasCriadas); assertEquals(1, outraConta.atividadesCriadas)
            assertEquals(2, db.atividadeDao().buscarTodas().size)
        } finally { db.close() }
    }
    @Test fun loteInvalidoNaoDeixaDisciplinasOuAtividadesParciais() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, CampusFlowDatabase::class.java).build()
        try {
            val previa = listOf(
                PreviaCursoMoodle(CursoMoodle(1,"Valida"), listOf(TarefaMoodle("Entrega",1_800_000_000_000,1))),
                PreviaCursoMoodle(CursoMoodle(2,"Invalida"), emptyList(), Long.MAX_VALUE))
            try { ImportadorMoodle(db).importar(7,previa); fail("Deve rejeitar todo lote") }
            catch (_: IllegalArgumentException) { }
            assertTrue(db.disciplinaDao().observarTodas().first().isEmpty())
            assertTrue(db.atividadeDao().buscarTodas().isEmpty())
        } finally { db.close() }
    }
    @Test fun migracaoCincoSeisPreservaDadosEValidaEsquema() = runBlocking<Unit> {
        val name = "moodle-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.everton.campusflow.data.CampusFlowDatabase/5.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i); val table = e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO disciplinas(id,nome,professor) VALUES(9,'Preservada','Ana')")
            old.execSQL("INSERT INTO atividades(id,titulo,disciplinaId,tipo,prazoEpochDay,prioridade,concluida,descricao,lembreteEnviado,lembretesAutomaticos) VALUES(5,'Original',9,'TAREFA',20000,'MEDIA',1,'Descricao',0,0)")
            old.version=5
        }
        val db=Room.databaseBuilder(context,CampusFlowDatabase::class.java,name).addMigrations(CampusFlowDatabase.MIGRATION_5_6, CampusFlowDatabase.MIGRATION_6_7).build()
        try {
            assertEquals("Ana",db.disciplinaDao().buscarPorId(9)!!.professor)
            assertTrue(db.atividadeDao().buscarPorId(5)!!.concluida)
            assertEquals(7,db.openHelper.readableDatabase.version)
            db.cursoImportadoDao().inserir(CursoImportado("origem",3,9))
            assertEquals(9L,db.cursoImportadoDao().buscar("origem",3))
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0,it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun conclusaoRemotaExigeOpcaoParaExistentesENuncaReabre() = runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        try {
            val importer=ImportadorMoodle(db)
            fun previa(envio:EnvioMoodle)=listOf(PreviaCursoMoodle(CursoMoodle(80,"Teste"),listOf(TarefaMoodle("Original",1_800_000_000_000,90,envio))))
            importer.importar(7,previa(EnvioMoodle.RASCUNHO))
            val salvo=db.atividadeDao().buscarTodas().single()
            assertFalse(salvo.concluida)
            db.atividadeDao().atualizar(salvo.copy(titulo="Edicao preservada"))
            importer.importar(7,previa(EnvioMoodle.ENVIADO))
            assertFalse(db.atividadeDao().buscarPorId(salvo.id)!!.concluida)
            val atualizacao=importer.importar(7,previa(EnvioMoodle.ENVIADO),true)
            assertEquals(1,atualizacao.conclusoesAtualizadas)
            assertEquals(salvo.copy(titulo="Edicao preservada",concluida=true),db.atividadeDao().buscarPorId(salvo.id))
            importer.importar(7,previa(EnvioMoodle.RASCUNHO),true)
            assertTrue(db.atividadeDao().buscarPorId(salvo.id)!!.concluida)
            importer.importar(8,previa(EnvioMoodle.ENVIADO))
            assertTrue(db.atividadeDao().buscarTodas().all { it.concluida })
        } finally { db.close() }
    }
    @Test fun professorSoGravaNaCriacaoENaoSobrescreveExistentes() = runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        try {
            val importer=ImportadorMoodle(db)
            val p=PreviaCursoMoodle(CursoMoodle(55,"Nova"),emptyList(),professor=" Ana; Bruno ")
            importer.importar(7,listOf(p))
            val nova=db.disciplinaDao().observarTodas().first().single()
            assertEquals("Ana; Bruno",nova.professor)
            importer.importar(7,listOf(p.copy(professor="Substituto")))
            assertEquals("Ana; Bruno",db.disciplinaDao().buscarPorId(nova.id)!!.professor)
            val manual=db.disciplinaDao().inserir(Disciplina(nome="Existente",professor="Manual"))
            importer.importar(7,listOf(p.copy(curso=CursoMoodle(56,"Associada"),disciplinaId=manual,professor="Remoto")))
            assertEquals("Manual",db.disciplinaDao().buscarPorId(manual)!!.professor)
        } finally { db.close() }
    }
}
