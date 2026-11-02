package com.everton.campusflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "controle_moodle", foreignKeys = [ForeignKey(entity = Atividade::class,
    parentColumns = ["id"], childColumns = ["atividadeId"], onDelete = ForeignKey.CASCADE)])
data class ControleMoodle(
    @PrimaryKey val atividadeId: Long,
    val tituloBase: String?, val prazoBase: Long?, val prazoConhecido: Boolean,
    val conclusaoBase: Boolean?,
    val tituloRemoto: String, val prazoRemoto: Long?, val conclusaoRemota: Boolean?,
    val tituloPendente: Boolean = false, val prazoPendente: Boolean = false,
    val conclusaoPendente: Boolean = false,
)

@Dao
interface ControleMoodleDao {
    @Query("SELECT * FROM controle_moodle WHERE atividadeId = :id")
    suspend fun buscar(id: Long): ControleMoodle?
    @Upsert suspend fun salvar(controle: ControleMoodle)
    @Query("SELECT * FROM controle_moodle WHERE tituloPendente OR prazoPendente OR conclusaoPendente ORDER BY atividadeId")
    fun observarPendentes(): Flow<List<ControleMoodle>>
}
