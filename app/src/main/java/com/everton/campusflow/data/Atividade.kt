package com.everton.campusflow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class TipoAtividade { PROVA, TRABALHO, TAREFA }
enum class PrioridadeAtividade { BAIXA, MEDIA, ALTA }

@Entity(
    tableName = "atividades",
    foreignKeys = [ForeignKey(
        entity = Disciplina::class,
        parentColumns = ["id"],
        childColumns = ["disciplinaId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("disciplinaId")],
)
data class Atividade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val disciplinaId: Long,
    val tipo: TipoAtividade,
    // Dia de calendário, sem horário nem conversão para meia-noite em UTC.
    val prazoEpochDay: Long,
    val prioridade: PrioridadeAtividade,
    val concluida: Boolean = false,
    val prazoExatoEpochMillis: Long? = null,
    val descricao: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val lembretesAutomaticos: Boolean = false,
    val avisoAtrasoDia: Long? = null,
    val lembretesAtivadosEm: Long? = null,
    val lembreteEpochMillis: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val lembreteEnviado: Boolean = false,
) {
    init {
        require(titulo.isNotBlank()) { "Informe o título da atividade" }
        require(disciplinaId > 0) { "Selecione uma disciplina válida" }
        LocalDate.ofEpochDay(prazoEpochDay)
    }

    // Recebe a data de referência para permitir testes sem depender do relógio.
    fun estaAtrasada(hoje: LocalDate, agora: Long = System.currentTimeMillis()): Boolean =
        !concluida && (prazoExatoEpochMillis?.let { agora >= it } ?: (prazoEpochDay < hoje.toEpochDay()))
}
