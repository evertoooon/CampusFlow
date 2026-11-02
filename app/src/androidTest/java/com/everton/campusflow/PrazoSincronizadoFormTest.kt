package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.ConciliacaoMoodle
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrazoSincronizadoFormTest {
    @Test fun salvarRascunhoPreservaPrazoSincronizadoEConclusao() = runBlocking<Unit> {
        val ins=InstrumentationRegistry.getInstrumentation()
        val app=ins.targetContext.applicationContext as Application
        val db=CampusFlowDatabase.getInstance(app)
        val d=db.disciplinaDao().inserir(Disciplina(nome="teste-prazo-${UUID.randomUUID()}",professor=""))
        val primeiro=Instant.now().plusSeconds(86400).toEpochMilli()
        val novo=primeiro+86400000L
        val id=db.atividadeDao().inserir(Atividade(titulo="Original",disciplinaId=d,tipo=TipoAtividade.TRABALHO,prioridade=PrioridadeAtividade.MEDIA,
            prazoEpochDay=Instant.ofEpochMilli(primeiro).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
            prazoExatoEpochMillis=primeiro))
        try {
            lateinit var vm:AtividadeFormViewModel
            ins.runOnMainSync { vm=AtividadeFormViewModel(app,SavedStateHandle(mapOf(AtividadeFormActivity.EXTRA_ATIVIDADE_ID to id))) }
            withTimeout(10000) { while(!vm.estado.value.pronto) delay(30) }
            db.atividadeDao().atualizar(ConciliacaoMoodle.comPrazo(db.atividadeDao().buscarPorId(id)!!,novo).copy(concluida=true))
            ins.runOnMainSync { vm.rascunho(descricao="Rascunho preservado"); vm.salvar() }
            withTimeout(10000) { while(!vm.estado.value.finalizado) { check(vm.estado.value.erro==null); delay(30) } }
            val salvo=db.atividadeDao().buscarPorId(id)!!
            assertEquals(novo,salvo.prazoExatoEpochMillis)
            assertEquals(Instant.ofEpochMilli(novo).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),salvo.prazoEpochDay)
            assertTrue(salvo.concluida)
            assertEquals("Rascunho preservado",salvo.descricao)
        } finally { db.atividadeDao().excluirPorId(id); db.disciplinaDao().excluirPorId(d) }
    }
}
