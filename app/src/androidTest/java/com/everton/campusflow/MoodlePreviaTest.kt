package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoodlePreviaTest {
    @Test fun selecaoPreviaFalhaParcialERotinaSemGravacao() = runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as Application
        val db=CampusFlowDatabase.getInstance(app)
        val antes=db.atividadeDao().buscarTodas()
        val disciplinasAntes=db.disciplinaDao().observarTodas().first()
        var falhar=false
        val fake=object:AcessoMoodle {
            override fun autenticar(usuario:String,senha:String)="token-ficticio"
            override fun site(token:String)=SiteMoodle(777,setOf("core_enrol_get_users_courses","mod_assign_get_assignments","core_enrol_get_enrolled_users"))
            override fun cursos(token:String,usuarioId:Long)=listOf(CursoMoodle(1,"Primeira",2),CursoMoodle(2,"Segunda",1))
            override fun professores(token:String,cursoId:Long):ProfessoresMoodle {
                if(cursoId==2L) throw FalhaMoodle(MotivoMoodle.ACESSO)
                return ProfessoresMoodle(listOf("Ana"),EstadoProfessores.ENCONTRADOS)
            }
            override fun tarefas(token:String,cursoId:Long):List<TarefaMoodle> {
                if(falhar && cursoId==2L) throw FalhaMoodle(MotivoMoodle.REDE)
                return listOf(TarefaMoodle("Entrega",1_800_000_000_000,cursoId*10),TarefaMoodle("Sem prazo",null,cursoId*10+1))
            }
        }
        val store=ViewModelStore()
        lateinit var model:MoodleViewModel
        instrumentation.runOnMainSync {
            model=ViewModelProvider(store,object:ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T:ViewModel> create(modelClass:Class<T>):T = MoodleViewModel(app,fake) as T
            })[MoodleViewModel::class.java]
            model.verificar("ficticio","ficticia")
        }
        try {
            withTimeout(10_000) { model.estado.first { !it.ocupado && it.resultado!=null } }
            assertTrue(model.estado.value.escolhas.isEmpty())
            instrumentation.runOnMainSync { model.escolher(1,true); model.escolher(2,true); model.consultar() }
            val previa=withTimeout(10_000) { model.estado.first { !it.ocupado && it.previa!=null } }.previa!!
            assertEquals(2,previa.size); assertNull(previa.first().tarefas.last().prazo)
            assertEquals("Ana",model.professor(previa.first()))
            assertEquals(EstadoProfessores.FALHOU,previa.last().professores.estado)
            instrumentation.runOnMainSync { model.editarProfessor(1,"Editado") }
            assertEquals("Editado",model.professor(previa.first()))
            assertEquals(antes,db.atividadeDao().buscarTodas())
            assertEquals(disciplinasAntes,db.disciplinaDao().observarTodas().first())
            falhar=true
            instrumentation.runOnMainSync { model.consultar() }
            val erro=withTimeout(10_000) { model.estado.first { !it.ocupado && it.erro!=null } }
            assertNull(erro.previa)
            assertEquals(MotivoMoodle.REDE,erro.erro)
            assertEquals(antes,db.atividadeDao().buscarTodas())
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
