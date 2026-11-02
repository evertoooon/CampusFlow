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
class ConexaoMoodleTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun cofreCifraTokenRestauraEApaga() {
        val nome="teste-cofre-${UUID.randomUUID()}"
        val cofre=CofreMoodle(context,nome)
        try {
            cofre.salvar(SessaoPersistida("token-ficticio-nao-real",7,"g",listOf(CursoMoodle(1,"Teste"))))
            val bytes=File(context.noBackupFilesDir,"$nome.bin").readBytes()
            assertFalse(String(bytes,Charsets.ISO_8859_1).contains("token-ficticio-nao-real"))
            assertEquals("token-ficticio-nao-real",CofreMoodle(context,nome).ler()!!.token)
            assertEquals(7L,cofre.ler()!!.usuario)
            cofre.apagar(); assertNull(cofre.ler())
        } finally { cofre.apagar() }
    }
    @Test fun syncRepeteSemDuplicarPreservaEdicoesEDesconexaoEmVooImpedeGravacao() = runBlocking<Unit> {
        val nome="teste-sync-${UUID.randomUUID()}"; val cofre=CofreMoodle(context,nome)
        val db=Room.inMemoryDatabaseBuilder(context,CampusFlowDatabase::class.java).build()
        val origem="${MoodleUpf.ORIGEM}/usuario/7"
        try {
            val d=db.disciplinaDao().inserir(Disciplina(nome="Teste",professor="Local"))
            db.cursoImportadoDao().inserir(CursoImportado(origem,1,d))
            val cursos=listOf(CursoMoodle(1,"Remota"))
            fun sessao(g:String)=SessaoPersistida("ficticio",7,g,cursos)
            cofre.salvar(sessao("g1"))
            var id=10L; var desconectar=false
            val fake=object:AcessoMoodle {
                override fun autenticar(usuario:String,senha:String)="ficticio"
                override fun site(token:String)=SiteMoodle(7,setOf("mod_assign_get_assignments"))
                override fun cursos(token:String,usuarioId:Long)=cursos
                override fun tarefas(token:String,cursoId:Long):List<TarefaMoodle> {
                    if(desconectar) cofre.apagar()
                    return listOf(TarefaMoodle("Remoto",1_800_000_000_000,id))
                }
            }
            ConexaoMoodle.executar(context,true,fake,cofre,db)
            val salvo=db.atividadeDao().buscarTodas().single()
            db.atividadeDao().atualizar(salvo.copy(titulo="Editada",concluida=true))
            ConexaoMoodle.executar(context,true,fake,cofre,db)
            assertEquals(salvo.copy(titulo="Editada",concluida=true),db.atividadeDao().buscarTodas().single())
            assertEquals("Local",db.disciplinaDao().buscarPorId(d)!!.professor)
            id=11; desconectar=true
            ConexaoMoodle.executar(context,true,fake,cofre,db)
            assertEquals(1,db.atividadeDao().buscarTodas().size)
            assertNull(cofre.ler())
        } finally { cofre.apagar(); db.close(); ConexaoMoodle.carregar(context) }
    }
}
