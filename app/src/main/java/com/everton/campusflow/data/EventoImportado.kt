package com.everton.campusflow.data

import androidx.room.*

@Entity(tableName = "eventos_importados", primaryKeys = ["origem", "uid"],
    indices = [Index(value = ["atividadeId"], unique = true)],
    foreignKeys = [ForeignKey(entity = Atividade::class, parentColumns = ["id"],
        childColumns = ["atividadeId"], onDelete = ForeignKey.CASCADE)])
data class EventoImportado(val origem: String, val uid: String, val atividadeId: Long, val fusoOriginal: String?)
