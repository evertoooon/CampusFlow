package com.everton.campusflow

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ConexaoSegundoPlanoTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private class Api : AcessoMoodle {
        var chamadas=0; var erro:FalhaMoodle?=null; var antesTarefas:()->Unit={}; var id=10L
        override fun autenticar(usuario:String,senha:String)="ficticio"
        override fun site(token:String):SiteMoodle { chamadas++; erro?.let { throw it }; return SiteMoodle(7,setOf("mod_assign_get_assignments")) }
        override fun cursos(token:String,usuarioId:Long)=listOf(CursoMoodle(1,"Teste"))
        override fun tarefas(token:String,cursoId:Long):List<TarefaMoodle> { antesTarefas(); return listOf(TarefaMoodle("Nova",1_800_000_000_000,id)) }
    }
    @Test fun estadoPersistidoArquivoAdulteradoERecuperacaoAtomicFile() {
        val nome="bg-cofre-${UUID.randomUUID()}"; val cofre=CofreMoodle(context,nome)
        val arquivo=File(context.noBackupFilesDir,"$nome.bin")
        try {
            val antiga=SessaoPersistida("ficticio",7,"g",listOf(CursoMoodle(1,"Teste")))
            cofre.salvar(antiga)
            assertFalse(cofre.ler()!!.segundoPlano); assertFalse(cofre.ler()!!.avisarNovas)
            cofre.salvar(antiga.com(tentativa=123,bloqueada=true,segundoPlano=true,avisarNovas=true))
            val restaurada=CofreMoodle(context,nome).ler()!!
            assertEquals(123L,restaurada.tentativa); assertTrue(restaurada.bloqueada)
            assertTrue(restaurada.segundoPlano); assertTrue(restaurada.avisarNovas)
            // Simula um backup pendente de AtomicFile em API 26+, somente no arquivo exclusivo deste teste.
            assertTrue(arquivo.renameTo(File(arquivo.path+".bak")))
            assertEquals("g",cofre.ler()!!.geracao)
            val bytes=arquivo.readBytes(); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); arquivo.writeBytes(bytes)
            try { cofre.ler(); fail("Adulteracao precisa ser rejeitada") } catch(_:javax.crypto.AEADBadTagException) { }
        } finally { cofre.apagar() }
    }
    @Test fun jobAntigoOptinAvisosEInterrupcaoAntesDoCommit() = runBlocking<Unit> {
        val nome="bg-motor-${UUID.randomUUID()}"; val cofre=CofreMoodle(context,nome)
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        val api=Api(); var avisos=0; val aviso=AvisoNovidades { avisos+=it }
        try {
            val d=db.disciplinaDao().inserir(Disciplina(nome="Local"))
            db.cursoImportadoDao().inserir(CursoImportado("${MoodleUpf.ORIGEM}/usuario/7",1,d))
            val s=SessaoPersistida("ficticio",7,"atual",listOf(CursoMoodle(1,"Remota")))
            cofre.salvar(s)
            assertEquals(ResultadoSync.IGNORADA,ConexaoMoodle.executar(context,true,api,cofre,db,"antiga",aviso))
            assertEquals(ResultadoSync.IGNORADA,ConexaoMoodle.executar(context,true,api,cofre,db,"atual",aviso))
            assertEquals(0,api.chamadas)
            cofre.salvar(s.com(segundoPlano=true,avisarNovas=true))
            assertEquals(ResultadoSync.ATUALIZADA,ConexaoMoodle.executar(context,true,api,cofre,db,"atual",aviso))
            assertEquals(1,avisos)
            ConexaoMoodle.executar(context,true,api,cofre,db,"atual",aviso)
            assertEquals(1,avisos); assertEquals(1,db.atividadeDao().buscarTodas().size)
            api.id=11; api.antesTarefas={ cofre.salvar(cofre.ler()!!.com(avisarNovas=false)) }
            ConexaoMoodle.executar(context,true,api,cofre,db,"atual",aviso)
            assertEquals(1,avisos); assertEquals(2,db.atividadeDao().buscarTodas().size)
            api.id=12; api.antesTarefas={ cofre.salvar(cofre.ler()!!.com(segundoPlano=false)) }
            assertEquals(ResultadoSync.IGNORADA,ConexaoMoodle.executar(context,true,api,cofre,db,"atual",aviso))
            assertEquals(2,db.atividadeDao().buscarTodas().size)
            cofre.salvar(s.com(segundoPlano=true)); api.antesTarefas={ cofre.apagar() }
            assertEquals(ResultadoSync.IGNORADA,ConexaoMoodle.executar(context,true,api,cofre,db,"atual",aviso))
            assertEquals(2,db.atividadeDao().buscarTodas().size)
        } finally { cofre.apagar(); db.close(); ConexaoMoodle.carregar(context) }
    }
    @Test fun tokenInvalidoPermaneceBloqueadoENaoRetentaRede() = runBlocking<Unit> {
        val nome="bg-invalido-${UUID.randomUUID()}"; val cofre=CofreMoodle(context,nome)
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build(); val api=Api()
        try {
            cofre.salvar(SessaoPersistida("ficticio",7,"g",listOf(CursoMoodle(1,"Teste")),segundoPlano=true))
            api.erro=FalhaMoodle(MotivoMoodle.ACESSO,true)
            assertEquals(ResultadoSync.BLOQUEADA,ConexaoMoodle.executar(context,true,api,cofre,db,"g"))
            assertTrue(CofreMoodle(context,nome).ler()!!.bloqueada)
            api.erro=null
            assertEquals(ResultadoSync.BLOQUEADA,ConexaoMoodle.executar(context,true,api,cofre,db,"g"))
            assertEquals(1,api.chamadas); assertTrue(db.atividadeDao().buscarTodas().isEmpty())
        } finally { cofre.apagar(); db.close(); ConexaoMoodle.carregar(context) }
    }
    @Test fun tentativaPersisteERetryPodeIgnorarIntervalo() = runBlocking<Unit> {
        val nome="bg-retry-${UUID.randomUUID()}"; val cofre=CofreMoodle(context,nome)
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build(); val api=Api()
        try {
            cofre.salvar(SessaoPersistida("ficticio",7,"g",listOf(CursoMoodle(1,"Teste")),segundoPlano=true))
            api.erro=FalhaMoodle(MotivoMoodle.REDE)
            assertEquals(ResultadoSync.FALHA_TRANSITORIA,ConexaoMoodle.executar(context,false,api,cofre,db,"g"))
            assertTrue(CofreMoodle(context,nome).ler()!!.tentativa>0)
            assertEquals(ResultadoSync.IGNORADA,ConexaoMoodle.executar(context,false,api,cofre,db,"g"))
            assertEquals(1,api.chamadas)
            assertEquals(ResultadoSync.FALHA_TRANSITORIA,ConexaoMoodle.executar(context,true,api,cofre,db,"g"))
            assertEquals(2,api.chamadas)
        } finally { cofre.apagar(); db.close(); ConexaoMoodle.carregar(context) }
    }
}
