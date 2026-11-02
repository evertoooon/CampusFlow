package com.everton.campusflow.moodle

import com.everton.campusflow.data.*
import com.everton.campusflow.importacao.PrazosIcs
import java.time.Instant
import java.time.ZoneId

data class ResultadoConciliacao(val atividade: Atividade, val controle: ControleMoodle)

object ConciliacaoMoodle {
    private data class Decisao<T>(val valor: T, val base: T?, val conhecida: Boolean, val pendente: Boolean)
    private fun <T> decidir(base: T?, conhecida: Boolean, local: T, remoto: T,
        automatico: Boolean, aplicavel: Boolean = true): Decisao<T> = when {
        local == remoto -> Decisao(local, remoto, true, false)
        conhecida && remoto == base -> Decisao(local, base, true, false)
        conhecida && local == base && automatico && aplicavel -> Decisao(remoto, remoto, true, false)
        else -> Decisao(local, base, conhecida, true)
    }
    fun conclusao(t: TarefaMoodle): Boolean? = when(t.envio) {
        EnvioMoodle.ENVIADO -> true
        EnvioMoodle.RASCUNHO, EnvioMoodle.PENDENTE -> false
        EnvioMoodle.DESCONHECIDO -> null
    }
    fun inicial(id: Long, t: TarefaMoodle) = ControleMoodle(id,t.nome,t.prazo,true,
        conclusao(t),t.nome,t.prazo,conclusao(t))

    fun conciliar(local: Atividade, anterior: ControleMoodle?, remoto: TarefaMoodle,
        automatico: Boolean, agora: Instant = Instant.now(), fuso: ZoneId = ZoneId.systemDefault()): ResultadoConciliacao {
        val c=anterior ?: ControleMoodle(local.id,null,null,false,null,remoto.nome,remoto.prazo,null)
        val titulo=decidir(c.tituloBase,c.tituloBase!=null,local.titulo,remoto.nome,automatico)
        val prazo=if(remoto.prazo==null) Decisao(local.prazoExatoEpochMillis,c.prazoBase,c.prazoConhecido,
            !(c.prazoConhecido && c.prazoBase==null))
            else decidir(c.prazoBase,c.prazoConhecido,local.prazoExatoEpochMillis,remoto.prazo,automatico)
        val remota=conclusao(remoto)
        val concluida=remota?.let { decidir(c.conclusaoBase,c.conclusaoBase!=null,local.concluida,it,automatico,it) }
        var atual=local.copy(titulo=titulo.valor,concluida=concluida?.valor?:local.concluida)
        if(prazo.valor!=local.prazoExatoEpochMillis && prazo.valor!=null) atual=comPrazo(atual,prazo.valor,agora,fuso)
        return ResultadoConciliacao(atual,c.copy(tituloBase=titulo.base,prazoBase=prazo.base,
            prazoConhecido=prazo.conhecida,conclusaoBase=concluida?.base?:c.conclusaoBase,
            tituloRemoto=remoto.nome,prazoRemoto=remoto.prazo,
            conclusaoRemota=remota?:c.conclusaoRemota,
            tituloPendente=titulo.pendente,prazoPendente=prazo.pendente,
            conclusaoPendente=concluida?.pendente?:c.conclusaoPendente))
    }
    fun comPrazo(a: Atividade, prazo: Long, agora: Instant = Instant.now(), fuso: ZoneId = ZoneId.systemDefault()) =
        a.copy(prazoExatoEpochMillis=prazo,prazoEpochDay=Instant.ofEpochMilli(prazo).atZone(fuso).toLocalDate().toEpochDay(),
            avisoAtrasoDia=if(a.lembretesAutomaticos) PrazosIcs.proximasNove(maxOf(agora,Instant.ofEpochMilli(prazo)),fuso) else a.avisoAtrasoDia)
}
