package com.everton.campusflow

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.CampusFlowDatabase
import com.everton.campusflow.data.Disciplina
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisciplinaPersistenceTest {
    @Test
    fun registrosMantemIdsEDadosAoReabrirBanco() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Banco isolado: não apaga nem modifica disciplinas cadastradas pelo usuário.
        val name = "disciplinas-test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
        try {
            val dao = db.disciplinaDao()
            assertTrue(withTimeout(5_000) { dao.observarTodas().first() }.isEmpty())
            val primeiroId = dao.inserir(Disciplina(nome = "Matemática"))
            val segundoId = dao.inserir(Disciplina(nome = "Matemática", professor = "Ana"))
            assertTrue(primeiroId > 0)
            assertNotEquals(primeiroId, segundoId)

            val editada = Disciplina(id = primeiroId, nome = "Matemática II", professor = "Carlos")
            assertEquals(1, dao.atualizar(editada))
            assertEquals(editada, dao.buscarPorId(primeiroId))
            assertEquals(0, dao.atualizar(Disciplina(id = Long.MAX_VALUE, nome = "Inexistente")))
            assertEquals(null, dao.buscarPorId(Long.MAX_VALUE))

            db.close()
            db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
            val registros = withTimeout(5_000) { db.disciplinaDao().observarTodas().first() }
            assertEquals(
                listOf(
                    Disciplina(id = segundoId, nome = "Matemática", professor = "Ana"),
                    editada,
                ),
                registros,
            )
            assertEquals(1, db.disciplinaDao().excluirPorId(primeiroId))
            assertEquals(0, db.disciplinaDao().excluirPorId(primeiroId))
            db.close()
            db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
            assertEquals(null, db.disciplinaDao().buscarPorId(primeiroId))
            assertEquals(
                listOf(Disciplina(id = segundoId, nome = "Matemática", professor = "Ana")),
                withTimeout(5_000) { db.disciplinaDao().observarTodas().first() },
            )
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
