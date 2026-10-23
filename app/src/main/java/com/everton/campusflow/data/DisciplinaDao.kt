package com.everton.campusflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM disciplinas ORDER BY nome COLLATE NOCASE, id")
    fun observarTodas(): Flow<List<Disciplina>>
}
