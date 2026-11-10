package com.everton.campusflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AtividadeDao {
    @Query("SELECT * FROM atividades ORDER BY id")
    suspend fun buscarTodas(): List<Atividade>

    @Query("UPDATE atividades SET lembreteEnviado = 1 WHERE id = :id AND lembreteEpochMillis = :horario AND concluida = 0 AND lembreteEnviado = 0")
    suspend fun marcarLembreteEnviado(id: Long, horario: Long): Int
    @Query("SELECT EXISTS(SELECT 1 FROM entregas_automaticas WHERE atividadeId = :id AND prazo = :prazo AND etapa = :etapa)")
    suspend fun etapaEntregue(id: Long, prazo: Long, etapa: String): Boolean

    @Insert suspend fun registrarEntrega(entrega: EntregaAutomatica)

    @Query("SELECT atividadeId FROM eventos_importados WHERE origem = :origem AND uid = :uid")
    suspend fun buscarImportado(origem: String, uid: String): Long?
    @Insert suspend fun registrarImportado(evento: EventoImportado)

    @Insert suspend fun inserir(atividade: Atividade): Long
    @Update suspend fun atualizar(atividade: Atividade): Int

    @Query("SELECT * FROM atividades WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Long): Atividade?

    @Query("""
        SELECT a.id, a.titulo, a.disciplinaId, d.nome AS disciplinaNome, COALESCE(d.professor, '') AS disciplinaProfessor,
               a.tipo, a.prazoEpochDay, a.prioridade, a.concluida, a.descricao, a.prazoExatoEpochMillis
        FROM atividades a
        INNER JOIN disciplinas d ON d.id = a.disciplinaId
        ORDER BY a.prazoEpochDay ASC, a.id ASC
    """)
    fun observarTodasComDisciplina(): Flow<List<AtividadeComDisciplina>>

    @Query("UPDATE atividades SET concluida = :concluida WHERE id = :id")
    suspend fun atualizarConcluida(id: Long, concluida: Boolean): Int

    @Query("DELETE FROM atividades WHERE id = :id")
    suspend fun excluirPorId(id: Long): Int
}
