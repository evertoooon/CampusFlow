package com.everton.campusflow

import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlanoAutomaticoTest {
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private val prazo = LocalDate.of(2026, 11, 10).toEpochDay()
    private fun instante(data: String) = LocalDateTime.parse(data).atZone(fuso).toInstant()
    private fun atividade() = Atividade(titulo = "Prova", disciplinaId = 1, tipo = TipoAtividade.PROVA,
        prazoEpochDay = prazo, prioridade = PrioridadeAtividade.MEDIA, lembretesAutomaticos = true,
        avisoAtrasoDia = prazo + 1)

    @Test fun vesperaEAtrasoAsNoveNoFusoLocal() {
        assertEquals(instante("2026-11-09T09:00").toEpochMilli(), PlanoAutomatico.horario(atividade(), EtapaAutomatica.VESPERA, fuso))
        assertEquals(instante("2026-11-11T09:00").toEpochMilli(), PlanoAutomatico.horario(atividade(), EtapaAutomatica.ATRASO, fuso))
        assertFalse(PlanoAutomatico.pertinente(atividade(), EtapaAutomatica.ATRASO, instante("2026-11-10T23:59"), fuso))
        assertTrue(PlanoAutomatico.pertinente(atividade(), EtapaAutomatica.ATRASO, instante("2026-11-11T09:00"), fuso))
    }
    @Test fun cadastroAtrasadoEscolheProximasNoveSemAdiarAoReconciliar() {
        assertEquals(prazo + 2, PlanoAutomatico.diaAtraso(prazo, instante("2026-11-12T08:00"), fuso))
        assertEquals(prazo + 3, PlanoAutomatico.diaAtraso(prazo, instante("2026-11-12T09:00"), fuso))
        assertEquals(prazo + 1, PlanoAutomatico.diaAtraso(prazo, instante("2026-11-10T15:00"), fuso))
    }
    @Test fun avisoDeVesperaNaoMenteSeAndroidEntregarDepoisOuCadastroForTardio() {
        assertFalse(PlanoAutomatico.pertinente(atividade(), EtapaAutomatica.VESPERA, instante("2026-11-10T00:01"), fuso))
        val tarde = atividade().copy(lembretesAtivadosEm = instante("2026-11-09T10:00").toEpochMilli())
        assertFalse(PlanoAutomatico.pertinente(tarde, EtapaAutomatica.VESPERA, instante("2026-11-09T10:01"), fuso))
    }
    @Test fun mudancaDeFusoRecalculaInstanteMantendoNoveLocais() {
        val utc = ZoneId.of("UTC")
        assertEquals(3 * 3_600_000L, PlanoAutomatico.horario(atividade(), EtapaAutomatica.VESPERA, fuso) -
            PlanoAutomatico.horario(atividade(), EtapaAutomatica.VESPERA, utc))
    }
}
