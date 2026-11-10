package com.everton.campusflow

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everton.campusflow.data.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtividadeListaDaoTest {
    @Test
    fun listaMantemVinculoOrdenaEConcluiSomenteOIdSelecionado() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "lista-atividades-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, CampusFlowDatabase::class.java, name).build()
        try {
            val disciplinas = db.disciplinaDao()
            val dao = db.atividadeDao()
            // Nomes iguais não podem misturar os vínculos nem os professores.
            val primeira = disciplinas.inserir(Disciplina(nome = "Mobile", professor = "Ana"))
            val segunda = disciplinas.inserir(Disciplina(nome = "Mobile", professor = "Bia"))
            val hoje = LocalDate.of(2026, 10, 26)
            val modelo = Atividade(
                titulo = "Trabalho", disciplinaId = primeira, tipo = TipoAtividade.TRABALHO,
                prazoEpochDay = hoje.toEpochDay(), prioridade = PrioridadeAtividade.ALTA,
                descricao = "Descrição que deve ser preservada",
            )
            val futuro = dao.inserir(modelo.copy(prazoEpochDay = hoje.plusDays(1).toEpochDay()))
            val id = dao.inserir(modelo)
            val empate = dao.inserir(modelo.copy(disciplinaId = segunda))
            val fluxo = dao.observarTodasComDisciplina()
            val lista = withTimeout(5_000) { fluxo.first() }
            assertEquals(listOf(id, empate, futuro), lista.map { it.id })
            assertEquals(listOf("Ana", "Bia", "Ana"), lista.map { it.disciplinaProfessor })
            assertEquals(listOf(primeira, segunda, primeira), lista.map { it.disciplinaId })

            // Simula uma edição anterior: concluir não pode sobrescrever os outros campos.
            val editada = modelo.copy(id = id, titulo = "Título atualizado", descricao = "Nova descrição")
            assertEquals(1, dao.atualizar(editada))
            assertEquals(1, dao.atualizarConcluida(id, true))
            assertEquals(editada.copy(concluida = true), dao.buscarPorId(id))
            assertEquals(false, dao.buscarPorId(empate)?.concluida)
            assertEquals(false, dao.buscarPorId(futuro)?.concluida)
            assertEquals(1, dao.atualizarConcluida(id, false))
            assertEquals(editada, dao.buscarPorId(id))
            assertEquals(0, dao.atualizarConcluida(Long.MAX_VALUE, true))

            assertEquals(1, disciplinas.atualizar(Disciplina(primeira, "Mobile II", "Ana Maria")))
            val renomeada = withTimeout(5_000) { fluxo.first { rows ->
                rows.first { it.id == id }.disciplinaNome == "Mobile II"
            } }.first { it.id == id }
            assertEquals("Ana Maria", renomeada.disciplinaProfessor)
            assertFalse(renomeada.estaAtrasada(hoje))
            assertTrue(renomeada.estaAtrasada(hoje.plusDays(1)))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
