package com.everton.campusflow.importacao

import androidx.room.withTransaction
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.PlanoAutomatico
import java.time.*
import java.time.format.DateTimeFormatter

object PrazosIcs {
    fun formatar(instante: Long): String = Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss VV"))
    fun proximasNove(instante: Instant, fuso: ZoneId): Long {
        val hoje = instante.atZone(fuso).toLocalDate()
        return (if (hoje.atTime(9, 0).atZone(fuso).toInstant() > instante) hoje else hoje.plusDays(1)).toEpochDay()
    }
}

data class EscolhaIcs(val evento: EventoIcs, val disciplinaId: Long)
data class ResultadoImportacao(val ids: List<Long>, val repetidos: Int)

class ImportadorIcs(private val db: CampusFlowDatabase) {
    companion object { const val ORIGEM = "ics-local" }
    suspend fun importar(escolhas: List<EscolhaIcs>, lembrar: Boolean, agora: Instant,
        fuso: ZoneId): ResultadoImportacao = db.withTransaction {
        require(escolhas.size in 1..3 && escolhas.map { it.evento.uid }.distinct().size == escolhas.size)
        // Valida o lote inteiro antes de gravar. Não cria disciplinas a partir de texto não confiável.
        escolhas.forEach { require(db.disciplinaDao().buscarPorId(it.disciplinaId) != null) }
        val dao = db.atividadeDao()
        val ids = mutableListOf<Long>()
        var repetidos = 0
        for ((evento, disciplina) in escolhas) {
            if (dao.buscarImportado(ORIGEM, evento.uid) != null) { repetidos++; continue }
            val dia = evento.instante?.let { Instant.ofEpochMilli(it).atZone(fuso).toLocalDate().toEpochDay() } ?: evento.dia
            val atividade = Atividade(titulo = evento.titulo, descricao = evento.descricao,
                disciplinaId = disciplina, tipo = TipoAtividade.TAREFA, prioridade = PrioridadeAtividade.MEDIA,
                prazoEpochDay = dia, prazoExatoEpochMillis = evento.instante,
                lembretesAutomaticos = lembrar, lembretesAtivadosEm = if (lembrar) agora.toEpochMilli() else null,
                avisoAtrasoDia = if (!lembrar) null else evento.instante?.let {
                    PrazosIcs.proximasNove(maxOf(agora, Instant.ofEpochMilli(it)), fuso)
                } ?: PlanoAutomatico.diaAtraso(dia, agora, fuso))
            val id = dao.inserir(atividade)
            dao.registrarImportado(EventoImportado(ORIGEM, evento.uid, id, evento.fuso))
            ids += id
        }
        ResultadoImportacao(ids, repetidos)
    }
}
