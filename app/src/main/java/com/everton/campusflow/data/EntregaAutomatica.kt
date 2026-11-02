package com.everton.campusflow.data

import androidx.room.Entity
import androidx.room.ForeignKey

// Histórico por prazo: concluir/reabrir e mudar o fuso não repetem uma etapa já entregue.
@Entity(tableName = "entregas_automaticas", primaryKeys = ["atividadeId", "prazo", "etapa"],
    foreignKeys = [ForeignKey(entity = Atividade::class, parentColumns = ["id"],
        childColumns = ["atividadeId"], onDelete = ForeignKey.CASCADE)])
data class EntregaAutomatica(val atividadeId: Long, val prazo: Long, val etapa: String)
