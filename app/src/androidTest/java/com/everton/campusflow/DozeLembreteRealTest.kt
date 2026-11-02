package com.everton.campusflow

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Teste dirigido; o controlador externo entra/sai de Doze entre as fases. */
@RunWith(AndroidJUnit4::class)
class DozeLembreteRealTest {
    @Test fun lembreteAdiadoPodeSerEntregueAoSairDoRepouso() = runBlocking<Unit> {
        val fase=InstrumentationRegistry.getArguments().getString("fase")
        assumeTrue("Teste dirigido com fase preparar, verificar ou limpar",fase in listOf("preparar","verificar","limpar"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=CampusFlowDatabase.getInstance(context)
        val dao=db.atividadeDao()
        val marcador=File(context.noBackupFilesDir,"teste-doze-lembrete.json")
        if(fase=="preparar") {
            check(!marcador.exists()) { "Resolver preparação anterior antes de criar outra" }
            check(Notificacoes(context).habilitado())
            val disciplina=db.disciplinaDao().inserir(Disciplina(nome="Teste Doze ${UUID.randomUUID()}"))
            var id:Long?=null
            try {
                val horario=System.currentTimeMillis()+90000
                id=dao.inserir(Atividade(titulo="Teste temporário — lembrete após Doze",disciplinaId=disciplina,
                    tipo=TipoAtividade.TAREFA,prioridade=PrioridadeAtividade.MEDIA,
                    prazoEpochDay=LocalDate.now().plusDays(1).toEpochDay(),lembreteEpochMillis=horario))
                Lembretes.sincronizar(context,id)
                val trabalho=WorkManager.getInstance(context).getWorkInfosForUniqueWork("lembrete-atividade-$id").get().single { !it.state.isFinished }
                marcador.writeText(JSONObject().put("atividade",id).put("disciplina",disciplina)
                    .put("horario",horario).put("trabalho",trabalho.id.toString()).toString())
            } catch(e:Exception) {
                id?.let { dao.excluirPorId(it); Lembretes.sincronizar(context,it) }
                db.disciplinaDao().excluirPorId(disciplina)
                throw e
            }
            return@runBlocking
        }
        check(marcador.exists())
        val m=JSONObject(marcador.readText())
        val id=m.getLong("atividade")
        val disciplina=m.getLong("disciplina")
        val atividade=dao.buscarPorId(id)
        check(atividade!=null && atividade.disciplinaId==disciplina &&
            atividade.titulo=="Teste temporário — lembrete após Doze" && atividade.lembreteEpochMillis==m.getLong("horario"))
        try {
            if(fase=="verificar") {
                val trabalhos=WorkManager.getInstance(context).getWorkInfosForUniqueWork("lembrete-atividade-$id").get()
                assertTrue(trabalhos.any { it.id.toString()==m.getString("trabalho") })
                val manager=context.getSystemService(NotificationManager::class.java)
                withTimeout(90000) {
                    while(manager.activeNotifications.none { it.tag=="atividade-$id" } || dao.buscarPorId(id)?.lembreteEnviado!=true) delay(250)
                }
                val aviso=manager.activeNotifications.single { it.tag=="atividade-$id" }
                assertTrue(aviso.postTime>=m.getLong("horario"))
            }
        } finally {
            dao.excluirPorId(id)
            Lembretes.sincronizar(context,id)
            db.disciplinaDao().excluirPorId(disciplina)
            check(marcador.delete())
        }
    }
}
