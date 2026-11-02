package com.everton.campusflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AgendaMoodleTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun agendamentoUnicoPorGeracaoEOptinSemTokenNoWorkManager() {
        val nome="teste-agenda-${UUID.randomUUID()}"; val work=WorkManager.getInstance(context)
        val s=SessaoPersistida("token-ficticio-segredo",7,UUID.randomUUID().toString(),listOf(CursoMoodle(1,"Teste")),segundoPlano=true)
        try {
            AgendaMoodle.reconciliar(context,s,nome)
            val primeiro=work.getWorkInfosForUniqueWork(nome).get().single { !it.state.isFinished }
            assertEquals(NetworkType.CONNECTED,primeiro.constraints.requiredNetworkType)
            assertEquals(3_600_000L,primeiro.periodicityInfo!!.repeatIntervalMillis)
            assertFalse(primeiro.tags.any { it.contains("token-ficticio") })
            AgendaMoodle.reconciliar(context,s,nome)
            assertEquals(primeiro.id,work.getWorkInfosForUniqueWork(nome).get().single { !it.state.isFinished }.id)
            AgendaMoodle.reconciliar(context,SessaoPersistida("ficticio",7,UUID.randomUUID().toString(),s.cursos,segundoPlano=true),nome)
            assertNotEquals(primeiro.id,work.getWorkInfosForUniqueWork(nome).get().single { !it.state.isFinished }.id)
            AgendaMoodle.reconciliar(context,null,nome)
            assertTrue(work.getWorkInfosForUniqueWork(nome).get().all { it.state.isFinished })
        } finally { work.cancelUniqueWork(nome).result.get() }
    }
    @Test fun workerSemGeracaoOuComGeracaoAntigaTerminaSemConsultarConta() = runBlocking<Unit> {
        val work=WorkManager.getInstance(context)
        for (comGeracao in listOf(false,true)) {
            val nome="teste-worker-${UUID.randomUUID()}"
            val builder=OneTimeWorkRequestBuilder<MoodleSyncWorker>()
            if(comGeracao) builder.setInputData(workDataOf(AgendaMoodle.GERACAO to UUID.randomUUID().toString()))
            try {
                val pedido=builder.build(); work.enqueueUniqueWork(nome,ExistingWorkPolicy.KEEP,pedido).result.get()
                withTimeout(15_000) {
                    while(work.getWorkInfoById(pedido.id).get()?.state?.isFinished!=true) delay(50)
                }
                assertEquals(WorkInfo.State.SUCCEEDED,work.getWorkInfoById(pedido.id).get()!!.state)
            } finally { work.cancelUniqueWork(nome).result.get() }
        }
    }
}
