package com.everton.campusflow.data

import java.time.LocalDate

data class AtividadeComDisciplina(
    val id: Long,
    val titulo: String,
    val disciplinaId: Long,
    val disciplinaNome: String,
    val disciplinaProfessor: String,
    val tipo: TipoAtividade,
    val prazoEpochDay: Long,
    val prioridade: PrioridadeAtividade,
    val concluida: Boolean,
    val descricao: String,
    val prazoExatoEpochMillis: Long? = null,
) {
    fun estaAtrasada(hoje: LocalDate, agora: Long = System.currentTimeMillis()): Boolean =
        !concluida && (prazoExatoEpochMillis?.let { agora >= it } ?: (prazoEpochDay < hoje.toEpochDay()))
}
