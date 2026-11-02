package com.everton.campusflow.data

import androidx.room.*

@Entity(tableName = "cursos_importados", primaryKeys = ["origem", "cursoId"],
    indices = [Index("disciplinaId")], foreignKeys = [ForeignKey(entity = Disciplina::class,
        parentColumns = ["id"], childColumns = ["disciplinaId"], onDelete = ForeignKey.CASCADE)])
data class CursoImportado(val origem: String, val cursoId: Long, val disciplinaId: Long)

@Dao
interface CursoImportadoDao {
    @Query("SELECT disciplinaId FROM cursos_importados WHERE origem = :origem AND cursoId = :cursoId")
    suspend fun buscar(origem: String, cursoId: Long): Long?
    @Insert suspend fun inserir(curso: CursoImportado)
}
