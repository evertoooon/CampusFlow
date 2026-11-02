package com.everton.campusflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Disciplina::class, Atividade::class, EntregaAutomatica::class, EventoImportado::class, CursoImportado::class, ControleMoodle::class], version = 7, exportSchema = true)
@TypeConverters(AtividadeConverters::class)
abstract class CampusFlowDatabase : RoomDatabase() {
    abstract fun disciplinaDao(): DisciplinaDao
    abstract fun atividadeDao(): AtividadeDao
    abstract fun cursoImportadoDao(): CursoImportadoDao
    abstract fun controleMoodleDao(): ControleMoodleDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS controle_moodle (atividadeId INTEGER NOT NULL, tituloBase TEXT, prazoBase INTEGER, prazoConhecido INTEGER NOT NULL, conclusaoBase INTEGER, tituloRemoto TEXT NOT NULL, prazoRemoto INTEGER, conclusaoRemota INTEGER, tituloPendente INTEGER NOT NULL, prazoPendente INTEGER NOT NULL, conclusaoPendente INTEGER NOT NULL, PRIMARY KEY(atividadeId), FOREIGN KEY(atividadeId) REFERENCES atividades(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cursos_importados (origem TEXT NOT NULL, cursoId INTEGER NOT NULL, disciplinaId INTEGER NOT NULL, PRIMARY KEY(origem, cursoId), FOREIGN KEY(disciplinaId) REFERENCES disciplinas(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cursos_importados_disciplinaId ON cursos_importados(disciplinaId)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE atividades ADD COLUMN prazoExatoEpochMillis INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS eventos_importados (origem TEXT NOT NULL, uid TEXT NOT NULL, atividadeId INTEGER NOT NULL, fusoOriginal TEXT, PRIMARY KEY(origem, uid), FOREIGN KEY(atividadeId) REFERENCES atividades(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_eventos_importados_atividadeId ON eventos_importados(atividadeId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE atividades ADD COLUMN lembretesAutomaticos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE atividades ADD COLUMN avisoAtrasoDia INTEGER")
                db.execSQL("ALTER TABLE atividades ADD COLUMN lembretesAtivadosEm INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS entregas_automaticas (atividadeId INTEGER NOT NULL, prazo INTEGER NOT NULL, etapa TEXT NOT NULL, PRIMARY KEY(atividadeId, prazo, etapa), FOREIGN KEY(atividadeId) REFERENCES atividades(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE atividades ADD COLUMN lembreteEpochMillis INTEGER")
                db.execSQL("ALTER TABLE atividades ADD COLUMN lembreteEnviado INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Somente acrescenta a nova tabela: não recria nem apaga disciplinas.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS atividades (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        titulo TEXT NOT NULL,
                        disciplinaId INTEGER NOT NULL,
                        tipo TEXT NOT NULL,
                        prazoEpochDay INTEGER NOT NULL,
                        prioridade TEXT NOT NULL,
                        concluida INTEGER NOT NULL,
                        descricao TEXT NOT NULL,
                        FOREIGN KEY(disciplinaId) REFERENCES disciplinas(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_atividades_disciplinaId ON atividades(disciplinaId)")
            }
        }

        @Volatile private var instance: CampusFlowDatabase? = null

        fun getInstance(context: Context): CampusFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CampusFlowDatabase::class.java,
                    "campusflow.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { instance = it }
            }
    }
}
