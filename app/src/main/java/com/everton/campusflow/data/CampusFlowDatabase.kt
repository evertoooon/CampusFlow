package com.everton.campusflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Disciplina::class], version = 1, exportSchema = true)
abstract class CampusFlowDatabase : RoomDatabase() {
    abstract fun disciplinaDao(): DisciplinaDao

    companion object {
        @Volatile private var instance: CampusFlowDatabase? = null

        fun getInstance(context: Context): CampusFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CampusFlowDatabase::class.java,
                    "campusflow.db",
                ).build().also { instance = it }
            }
    }
}
