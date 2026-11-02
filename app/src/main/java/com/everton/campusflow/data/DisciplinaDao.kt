package com.everton.campusflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

enum class ResultadoExclusaoDisciplina { EXCLUIDA, COM_ATIVIDADES, INEXISTENTE }

@Dao
interface DisciplinaDao {
    @Insert
    suspend fun inserir(disciplina: Disciplina): Long

    @Query("SELECT * FROM disciplinas WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Long): Disciplina?

    @Update
    suspend fun atualizar(disciplina: Disciplina): Int

    @Query("DELETE FROM disciplinas WHERE id = :id")
    suspend fun excluirPorId(id: Long): Int

    @Query("SELECT COUNT(*) FROM atividades WHERE disciplinaId = :id")
    suspend fun contarAtividades(id: Long): Int

    @Transaction
    suspend fun excluirSeSemAtividades(id: Long): ResultadoExclusaoDisciplina {
        // A consulta e a exclusão compartilham a transação; a FK protege também o acesso direto.
        if (contarAtividades(id) > 0) return ResultadoExclusaoDisciplina.COM_ATIVIDADES
        return if (excluirPorId(id) == 1) ResultadoExclusaoDisciplina.EXCLUIDA
        else ResultadoExclusaoDisciplina.INEXISTENTE
    }

    @Query("SELECT * FROM disciplinas ORDER BY nome COLLATE NOCASE, id")
    fun observarTodas(): Flow<List<Disciplina>>
}
