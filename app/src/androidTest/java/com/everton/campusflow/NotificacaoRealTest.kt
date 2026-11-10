package com.everton.campusflow

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Integra WorkManager, canal Android e PendingIntent reais. Não concede permissões. */
@RunWith(AndroidJUnit4::class)
class NotificacaoRealTest {
    @Test fun entregaRealAbreAtividadeECancelaAoConcluir() = runBlocking<Unit> {
        val ins=InstrumentationRegistry.getInstrumentation()
        val context=ins.targetContext
        assumeTrue("O usuário precisa permitir notificações e habilitar o canal",Notificacoes(context).habilitado())
        val hoje=LocalDate.now()
        val inicio=hoje.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assumeTrue("Executar depois das 9h locais, sem alterar o relógio",System.currentTimeMillis()>=hoje.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val db=CampusFlowDatabase.getInstance(context)
        val dao=db.atividadeDao()
        val manager=context.getSystemService(NotificationManager::class.java)
        val tag="Teste de notificação ${UUID.randomUUID()}"
        val disciplina=db.disciplinaDao().inserir(Disciplina(nome=tag))
        val ids=mutableListOf<Long>()
        suspend fun aguardar(condicao:suspend ()->Boolean) = withTimeout(90000) {
            while(!condicao()) delay(200)
        }
        val scenario=ActivityScenario.launch<AtividadesActivity>(Intent(context,AtividadesActivity::class.java))
        try {
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            for((sufixo,dia) in listOf("Véspera" to hoje.plusDays(1),"Atraso" to hoje.minusDays(1))) {
                val id=dao.inserir(Atividade(titulo="$tag — $sufixo",disciplinaId=disciplina,
                    tipo=TipoAtividade.TAREFA,prioridade=PrioridadeAtividade.MEDIA,prazoEpochDay=dia.toEpochDay(),
                    lembretesAutomaticos=true,lembretesAtivadosEm=inicio,avisoAtrasoDia=hoje.toEpochDay()))
                ids+=id
                Lembretes.sincronizar(context,id)
            }
            aguardar { ids.all { id-> manager.activeNotifications.any { it.tag=="atividade-$id" } } }
            for((indice,id) in ids.withIndex()) {
                val etapa=if(indice==0) EtapaAutomatica.VESPERA else EtapaAutomatica.ATRASO
                aguardar { dao.etapaEntregue(id,dao.buscarPorId(id)!!.prazoEpochDay,etapa.name) }
                val aviso=manager.activeNotifications.first { it.tag=="atividade-$id" }
                assertEquals(context.getString(if(indice==0) R.string.reminder_before_body else R.string.reminder_overdue_body),
                    aviso.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                assertNotNull(aviso.notification.contentIntent)
                // Executa o mesmo PendingIntent usado pelo toque no aviso do sistema.
                aviso.notification.contentIntent.send()
                aguardar {
                    var correta=false
                    ins.runOnMainSync {
                        correta=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .any { it is AtividadeFormActivity && it.intent.getLongExtra(AtividadeFormActivity.EXTRA_ATIVIDADE_ID,0)==id }
                    }
                    correta
                }
                assertEquals(1,dao.atualizarConcluida(id,true))
                Lembretes.sincronizar(context,id)
                aguardar { manager.activeNotifications.none { it.tag=="atividade-$id" } }
                assertEquals(1,dao.atualizarConcluida(id,false))
                Lembretes.sincronizar(context,id)
                assertTrue(dao.etapaEntregue(id,dao.buscarPorId(id)!!.prazoEpochDay,etapa.name))
                assertTrue(manager.activeNotifications.none { it.tag=="atividade-$id" })
                ins.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<AtividadeFormActivity>().forEach { it.finish() }
                }
            }
        } finally {
            ins.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AtividadeFormActivity>().filter { it.intent.getLongExtra(AtividadeFormActivity.EXTRA_ATIVIDADE_ID,0) in ids }
                    .forEach { it.finish() }
            }
            scenario.close()
            for(id in ids) { dao.excluirPorId(id); Lembretes.sincronizar(context,id) }
            db.disciplinaDao().excluirPorId(disciplina)
        }
    }
}
