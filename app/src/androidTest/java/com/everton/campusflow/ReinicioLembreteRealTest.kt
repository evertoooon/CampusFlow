package com.everton.campusflow

import android.app.NotificationManager
import android.os.SystemClock
import android.provider.Settings
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

/** Teste dirigido em duas fases, com reinício real do Android entre execuções.
 * Não executa na bateria normal sem o argumento fase. */
@RunWith(AndroidJUnit4::class)
class ReinicioLembreteRealTest {
    @Test fun lembretePersisteEEntregaDepoisDoBoot() = runBlocking<Unit> {
        val fase=InstrumentationRegistry.getArguments().getString("fase")
        assumeTrue("Teste dirigido: informar fase preparar, verificar ou limpar",fase in listOf("preparar","verificar","limpar"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=CampusFlowDatabase.getInstance(context)
        val dao=db.atividadeDao()
        val marcador=File(context.noBackupFilesDir,"teste-reinicio-lembrete.json")
        if(fase=="preparar") {
            check(!marcador.exists()) { "Já existe teste pendente; verificar ou limpar antes de preparar outro" }
            check(Notificacoes(context).habilitado()) { "Notificações precisam estar habilitadas pelo usuário" }
            val boot=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
            check(boot>=0)
            val disciplina=db.disciplinaDao().inserir(Disciplina(nome="Teste reinício ${UUID.randomUUID()}"))
            var id:Long?=null
            try {
                val horario=System.currentTimeMillis()+180000
                id=dao.inserir(Atividade(titulo="Teste temporário — lembrete após reinício",disciplinaId=disciplina,
                    tipo=TipoAtividade.TAREFA,prioridade=PrioridadeAtividade.MEDIA,
                    prazoEpochDay=LocalDate.now().plusDays(1).toEpochDay(),lembreteEpochMillis=horario))
                Lembretes.sincronizar(context,id)
                val trabalho=WorkManager.getInstance(context).getWorkInfosForUniqueWork("lembrete-atividade-$id").get()
                    .single { !it.state.isFinished }
                marcador.writeText(JSONObject().put("atividade",id).put("disciplina",disciplina)
                    .put("horario",horario).put("boot",boot).put("trabalho",trabalho.id.toString()).toString())
                println("REINICIO_PREPARADO horario=$horario boot=$boot trabalho=${trabalho.id}")
            } catch(e:Exception) {
                id?.let { dao.excluirPorId(it); Lembretes.sincronizar(context,it) }
                db.disciplinaDao().excluirPorId(disciplina)
                throw e
            }
            return@runBlocking
        }
        check(marcador.exists()) { "Preparar o lembrete antes de verificar" }
        val m=JSONObject(marcador.readText())
        val id=m.getLong("atividade")
        val disciplina=m.getLong("disciplina")
        // Confere a identidade antes de qualquer limpeza de dados persistidos.
        val atividade=dao.buscarPorId(id)
        check(atividade!=null && atividade.disciplinaId==disciplina &&
            atividade.titulo=="Teste temporário — lembrete após reinício" && atividade.lembreteEpochMillis==m.getLong("horario"))
        try {
            if(fase=="verificar") {
                val boot=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
                assertTrue("O Android precisa ter reiniciado de verdade",boot>m.getInt("boot"))
                val trabalhos=WorkManager.getInstance(context).getWorkInfosForUniqueWork("lembrete-atividade-$id").get()
                assertTrue("O mesmo trabalho precisa persistir",trabalhos.any { it.id.toString()==m.getString("trabalho") })
                val manager=context.getSystemService(NotificationManager::class.java)
                val restante=(m.getLong("horario")-System.currentTimeMillis()).coerceAtLeast(0)
                withTimeout(restante+90000) {
                    while(manager.activeNotifications.none { it.tag=="atividade-$id" } || dao.buscarPorId(id)?.lembreteEnviado!=true) delay(250)
                }
                val aviso=manager.activeNotifications.single { it.tag=="atividade-$id" }
                val inicioBoot=System.currentTimeMillis()-SystemClock.elapsedRealtime()
                assertTrue("A publicação precisa ter ocorrido depois do reinício",aviso.postTime>=inicioBoot)
                assertTrue(aviso.postTime>=m.getLong("horario"))
                println("REINICIO_VALIDADO bootAntes=${m.getInt("boot")} bootDepois=$boot horario=${m.getLong("horario")} publicado=${aviso.postTime}")
            }
        } finally {
            dao.excluirPorId(id)
            Lembretes.sincronizar(context,id)
            db.disciplinaDao().excluirPorId(disciplina)
            check(marcador.delete())
        }
    }
}
