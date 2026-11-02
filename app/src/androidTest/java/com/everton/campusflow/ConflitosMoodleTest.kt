package com.everton.campusflow

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ConflitosMoodleTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val prazo=1_800_000_000_000L
    private val original=TarefaMoodle("Original",prazo,10,EnvioMoodle.PENDENTE)
    private fun previa(t:TarefaMoodle)=listOf(PreviaCursoMoodle(CursoMoodle(1,"Curso"),listOf(t)))
    @Test fun mergeAutomaticoConflitoPorCampoEManterNaoRepete() = runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        try {
            val importer=ImportadorMoodle(db)
            importer.importar(7,previa(original))
            val id=db.atividadeDao().buscarTodas().single().id
            val mudou=original.copy(nome="Professor",prazo=prazo+86400000,envio=EnvioMoodle.ENVIADO)
            val r=importer.importar(7,previa(mudou),conciliarAutomaticamente=true)
            assertEquals(listOf(id),r.alteradas)
            val a=db.atividadeDao().buscarPorId(id)!!
            assertEquals("Professor",a.titulo); assertTrue(a.concluida); assertEquals(mudou.prazo,a.prazoExatoEpochMillis)
            db.atividadeDao().atualizar(a.copy(titulo="Meu titulo",descricao="Minha anotacao"))
            val novo=mudou.copy(nome="Professor mudou novamente",prazo=prazo+172800000)
            importer.importar(7,previa(novo),conciliarAutomaticamente=true)
            val c=db.controleMoodleDao().buscar(id)!!; val local=db.atividadeDao().buscarPorId(id)!!
            assertTrue(c.tituloPendente); assertFalse(c.prazoPendente)
            assertEquals("Meu titulo",local.titulo); assertEquals(novo.prazo,local.prazoExatoEpochMillis)
            assertEquals(ResultadoResolucao.APLICADA,ResolverConflitoMoodle(db).resolver(c,local,CampoMoodle.TITULO,false))
            importer.importar(7,previa(novo),conciliarAutomaticamente=true)
            assertFalse(db.controleMoodleDao().buscar(id)!!.tituloPendente)
            assertEquals("Meu titulo",db.atividadeDao().buscarPorId(id)!!.titulo)
            assertEquals("Minha anotacao",db.atividadeDao().buscarPorId(id)!!.descricao)
        } finally { db.close() }
    }
    @Test fun resolucaoDesatualizadaNaoSobrescreveEdicoesNovas() = runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        try {
            val importer=ImportadorMoodle(db); importer.importar(7,previa(original))
            val a=db.atividadeDao().buscarTodas().single().copy(titulo="Meu titulo")
            db.atividadeDao().atualizar(a)
            importer.importar(7,previa(original.copy(nome="Remoto A")),conciliarAutomaticamente=true)
            val c=db.controleMoodleDao().buscar(a.id)!!
            importer.importar(7,previa(original.copy(nome="Remoto B")),conciliarAutomaticamente=true)
            assertEquals(ResultadoResolucao.DESATUALIZADA,ResolverConflitoMoodle(db).resolver(c,a,CampoMoodle.TITULO,true))
            val novo=db.controleMoodleDao().buscar(a.id)!!
            db.atividadeDao().atualizar(a.copy(titulo="Edicao depois da tela"))
            assertEquals(ResultadoResolucao.DESATUALIZADA,ResolverConflitoMoodle(db).resolver(novo,a,CampoMoodle.TITULO,true))
            val local=db.atividadeDao().buscarPorId(a.id)!!
            db.atividadeDao().atualizar(local.copy(descricao="Outra edicao independente"))
            assertEquals(ResultadoResolucao.APLICADA,ResolverConflitoMoodle(db).resolver(novo,local,CampoMoodle.TITULO,true))
            assertEquals("Remoto B",db.atividadeDao().buscarPorId(a.id)!!.titulo)
            assertEquals("Outra edicao independente",db.atividadeDao().buscarPorId(a.id)!!.descricao)
        } finally { db.close() }
    }
    @Test fun reaberturaSoExplicitaEPrazoRemovidoContinuaObrigatorio() = runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        try {
            val importer=ImportadorMoodle(db); importer.importar(7,previa(original.copy(envio=EnvioMoodle.ENVIADO)))
            val id=db.atividadeDao().buscarTodas().single().id
            importer.importar(7,previa(original.copy(prazo=null)),conciliarAutomaticamente=true)
            val a=db.atividadeDao().buscarPorId(id)!!; val c=db.controleMoodleDao().buscar(id)!!
            assertTrue(a.concluida); assertTrue(c.conclusaoPendente); assertTrue(c.prazoPendente)
            assertEquals(ResultadoResolucao.PRAZO_OBRIGATORIO,ResolverConflitoMoodle(db).resolver(c,a,CampoMoodle.PRAZO,true))
            assertEquals(ResultadoResolucao.APLICADA,ResolverConflitoMoodle(db).resolver(c,a,CampoMoodle.CONCLUSAO,true))
            assertFalse(db.atividadeDao().buscarPorId(id)!!.concluida)
            val atual=db.controleMoodleDao().buscar(id)!!; val local=db.atividadeDao().buscarPorId(id)!!
            assertEquals(ResultadoResolucao.APLICADA,ResolverConflitoMoodle(db).resolver(atual,local,CampoMoodle.PRAZO,false))
            importer.importar(7,previa(original.copy(prazo=null)),conciliarAutomaticamente=true)
            assertFalse(db.controleMoodleDao().buscar(id)!!.prazoPendente)
            assertEquals(prazo,db.atividadeDao().buscarPorId(id)!!.prazoExatoEpochMillis)
            db.atividadeDao().excluirPorId(id); assertNull(db.controleMoodleDao().buscar(id))
        } finally { db.close() }
    }
    @Test fun migracaoSeisSeteNaoInventaHistoricoERevisaLegado() = runBlocking<Unit> {
        val name="conflito-migracao-${UUID.randomUUID()}.db"; val file=context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val schema=JSONObject(instrumentation.context.assets.open("com.everton.campusflow.data.CampusFlowDatabase/6.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i); val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices")?:org.json.JSONArray()
                for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=schema.getJSONArray("setupQueries"); for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO disciplinas(id,nome,professor) VALUES(1,'Local','Ana')")
            old.execSQL("INSERT INTO atividades(id,titulo,disciplinaId,tipo,prazoEpochDay,prioridade,concluida,descricao,prazoExatoEpochMillis) VALUES(9,'Meu titulo antigo',1,'TAREFA',20833,'ALTA',1,'Anotacao',1800000000000)")
            old.execSQL("INSERT INTO cursos_importados VALUES('https://ead.upf.br/usuario/7',1,1)")
            old.execSQL("INSERT INTO eventos_importados VALUES('https://ead.upf.br/usuario/7','assign/10',9,'UTC')")
            old.version=6
        }
        val db=Room.databaseBuilder(context,CampusFlowDatabase::class.java,name).addMigrations(CampusFlowDatabase.MIGRATION_6_7).build()
        try {
            val antes=db.atividadeDao().buscarPorId(9)!!; assertNull(db.controleMoodleDao().buscar(9))
            assertEquals(7,db.openHelper.readableDatabase.version)
            ImportadorMoodle(db).importar(7,previa(original),conciliarAutomaticamente=true)
            assertEquals(antes,db.atividadeDao().buscarPorId(9))
            assertTrue(db.controleMoodleDao().buscar(9)!!.tituloPendente)
            assertTrue(db.controleMoodleDao().buscar(9)!!.conclusaoPendente)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0,it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
