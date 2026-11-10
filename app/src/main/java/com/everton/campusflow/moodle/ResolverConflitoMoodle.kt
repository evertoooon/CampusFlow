package com.everton.campusflow.moodle

import androidx.room.withTransaction
import com.everton.campusflow.data.*

enum class CampoMoodle { TITULO, PRAZO, CONCLUSAO }
enum class ResultadoResolucao { APLICADA, DESATUALIZADA, INEXISTENTE, PRAZO_OBRIGATORIO }

class ResolverConflitoMoodle(private val db: CampusFlowDatabase) {
    suspend fun resolver(esperado: ControleMoodle, localEsperada: Atividade, campo: CampoMoodle,
        usarMoodle: Boolean): ResultadoResolucao = db.withTransaction {
        val atual=db.atividadeDao().buscarPorId(esperado.atividadeId) ?: return@withTransaction ResultadoResolucao.INEXISTENTE
        val c=db.controleMoodleDao().buscar(atual.id) ?: return@withTransaction ResultadoResolucao.INEXISTENTE
        if(c!=esperado) return@withTransaction ResultadoResolucao.DESATUALIZADA
        val mudou=when(campo) {
            CampoMoodle.TITULO -> atual.titulo!=localEsperada.titulo || !c.tituloPendente
            CampoMoodle.PRAZO -> atual.prazoExatoEpochMillis!=localEsperada.prazoExatoEpochMillis ||
                atual.prazoEpochDay!=localEsperada.prazoEpochDay || !c.prazoPendente
            CampoMoodle.CONCLUSAO -> atual.concluida!=localEsperada.concluida || !c.conclusaoPendente
        }
        if(mudou) return@withTransaction ResultadoResolucao.DESATUALIZADA
        var atividade=atual
        val resolvido=when(campo) {
            CampoMoodle.TITULO -> {
                if(usarMoodle) atividade=atual.copy(titulo=c.tituloRemoto)
                c.copy(tituloBase=c.tituloRemoto,tituloPendente=false)
            }
            CampoMoodle.PRAZO -> {
                if(usarMoodle) {
                    val prazo=c.prazoRemoto ?: return@withTransaction ResultadoResolucao.PRAZO_OBRIGATORIO
                    atividade=ConciliacaoMoodle.comPrazo(atual,prazo)
                }
                c.copy(prazoBase=c.prazoRemoto,prazoConhecido=true,prazoPendente=false)
            }
            CampoMoodle.CONCLUSAO -> {
                val concluida=c.conclusaoRemota ?: return@withTransaction ResultadoResolucao.DESATUALIZADA
                if(usarMoodle) atividade=atual.copy(concluida=concluida)
                c.copy(conclusaoBase=concluida,conclusaoPendente=false)
            }
        }
        if(atividade!=atual) check(db.atividadeDao().atualizar(atividade)==1)
        db.controleMoodleDao().salvar(resolvido)
        ResultadoResolucao.APLICADA
    }
}
