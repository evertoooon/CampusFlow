package com.everton.campusflow

import com.everton.campusflow.data.Atividade
import com.everton.campusflow.data.PrioridadeAtividade
import com.everton.campusflow.data.TipoAtividade
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AtividadePrazoTest {
    private val hoje = LocalDate.of(2026, 10, 26)
    private fun atividade(data: LocalDate) = Atividade(
        titulo = "Trabalho final", disciplinaId = 1, tipo = TipoAtividade.TRABALHO,
        prazoEpochDay = data.toEpochDay(), prioridade = PrioridadeAtividade.MEDIA,
    )

    @Test fun prazoDeHojeNaoEstaAtrasado() {
        assertFalse(atividade(hoje).estaAtrasada(hoje))
        assertFalse(atividade(hoje.plusDays(1)).estaAtrasada(hoje))
        assertTrue(atividade(hoje.minusDays(1)).estaAtrasada(hoje))
    }

    @Test fun concluidaNaoEstaAtrasadaMesmoAposPrazo() {
        assertFalse(atividade(hoje.minusDays(5)).copy(concluida = true).estaAtrasada(hoje))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tituloSomenteEspacosEhRecusado() {
        atividade(hoje).copy(titulo = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun idDeDisciplinaNaoSelecionadaEhRecusado() {
        atividade(hoje).copy(disciplinaId = 0)
    }
}
