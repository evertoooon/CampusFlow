package com.everton.campusflow

import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ConciliacaoMoodleTest {
    private val prazo=Instant.parse("2026-11-10T21:00:00Z").toEpochMilli()
    private val remoto=TarefaMoodle("Original",prazo,10,EnvioMoodle.PENDENTE)
    private val local=Atividade(1,"Original",2,TipoAtividade.TRABALHO,LocalDate.of(2026,11,10).toEpochDay(),
        PrioridadeAtividade.ALTA,descricao="Minha anotacao",prazoExatoEpochMillis=prazo)
    private val base=ConciliacaoMoodle.inicial(1,remoto)
    private fun merge(a:Atividade=local,c:ControleMoodle?=base,t:TarefaMoodle=remoto,auto:Boolean=true)=
        ConciliacaoMoodle.conciliar(a,c,t,auto,Instant.parse("2026-10-30T12:00:00Z"),ZoneId.of("America/Sao_Paulo"))
    @Test fun soRemotoMudouAtualizaSemPerderCamposLocais() {
        val r=merge(t=remoto.copy(nome="Novo",prazo=prazo+86400000,envio=EnvioMoodle.ENVIADO))
        assertEquals("Novo",r.atividade.titulo); assertTrue(r.atividade.concluida)
        assertEquals(prazo+86400000,r.atividade.prazoExatoEpochMillis)
        assertEquals(local.descricao,r.atividade.descricao); assertEquals(local.prioridade,r.atividade.prioridade)
        assertFalse(r.controle.tituloPendente); assertFalse(r.controle.prazoPendente)
    }
    @Test fun soLocalMudouPreservaSemConflito() {
        val a=local.copy(titulo="Meu titulo",concluida=true)
        val r=merge(a=a)
        assertEquals(a,r.atividade); assertFalse(r.controle.tituloPendente); assertFalse(r.controle.conclusaoPendente)
    }
    @Test fun doisLadosMudaramPreservaLocalEPedeRevisao() {
        val a=local.copy(titulo="Meu titulo",prazoExatoEpochMillis=prazo+1000)
        val r=merge(a=a,t=remoto.copy(nome="Do professor",prazo=prazo+2000))
        assertEquals(a,r.atividade); assertTrue(r.controle.tituloPendente); assertTrue(r.controle.prazoPendente)
        assertEquals("Original",r.controle.tituloBase)
    }
    @Test fun nuncaReabreAutomaticamenteEIgnoraTentativaDesconhecida() {
        val a=local.copy(concluida=true); val b=base.copy(conclusaoBase=true)
        val r=merge(a,b)
        assertTrue(r.atividade.concluida); assertTrue(r.controle.conclusaoPendente)
        val desconhecido=merge(a,r.controle,remoto.copy(envio=EnvioMoodle.DESCONHECIDO))
        assertTrue(desconhecido.atividade.concluida); assertTrue(desconhecido.controle.conclusaoPendente)
        assertEquals(false,desconhecido.controle.conclusaoRemota)
    }
    @Test fun legadoSemBaseNaoPresumeIntactoEConvergenciaEstabeleceReferencia() {
        val r=merge(c=null,t=remoto.copy(nome="Novo",envio=EnvioMoodle.ENVIADO))
        assertEquals(local,r.atividade); assertTrue(r.controle.tituloPendente); assertTrue(r.controle.conclusaoPendente)
        assertTrue(r.controle.prazoConhecido)
        val igual=merge(c=null)
        assertFalse(igual.controle.tituloPendente); assertEquals("Original",igual.controle.tituloBase)
    }
    @Test fun prazoRemovidoNaoInventaDataEManualNaoAplicaAutomaticamente() {
        val r=merge(t=remoto.copy(prazo=null))
        assertEquals(local,r.atividade); assertTrue(r.controle.prazoPendente)
        val manual=merge(t=remoto.copy(nome="Novo"),auto=false)
        assertEquals(local,manual.atividade); assertTrue(manual.controle.tituloPendente)
        val convertido=merge(a=local.copy(prazoExatoEpochMillis=null),t=remoto.copy(prazo=prazo+1000))
        assertTrue(convertido.controle.prazoPendente)
    }
}
