package com.everton.campusflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disciplinas")
data class Disciplina(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val professor: String = "",
)
