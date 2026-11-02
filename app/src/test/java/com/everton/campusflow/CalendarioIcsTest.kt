package com.everton.campusflow

import com.everton.campusflow.importacao.*
import com.everton.campusflow.data.*
import com.everton.campusflow.lembretes.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CalendarioIcsTest {
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private fun calendario(eventos: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n$eventos\r\nEND:VCALENDAR"
    private fun evento(inicio: String, uid: String = "teste", extra: String = "") =
        "BEGIN:VEVENT\r\nUID:$uid\r\nSUMMARY:Entrega\\, Mobile\r\n$inicio\r\n$extra\r\nEND:VEVENT"

    @Test fun preservaInstanteUtcMesmoQuandoDataLocalEDiferente() {
        val e = LeitorIcs.ler(calendario(evento("DTSTART:20261110T010000Z")), fuso).eventos.single()
        assertEquals(Instant.parse("2026-11-10T01:00:00Z").toEpochMilli(), e.instante)
        assertEquals(LocalDate.of(2026,11,9).toEpochDay(), e.dia)
        assertEquals("Entrega, Mobile", e.titulo)
    }
    @Test fun interpretaFusoIanaEDataInteiraSemInventarHora() {
        val e = LeitorIcs.ler(calendario(evento("DTSTART;TZID=America/Sao_Paulo:20261110T180000")), fuso).eventos.single()
        assertEquals(Instant.parse("2026-11-10T21:00:00Z").toEpochMilli(), e.instante)
        val d = LeitorIcs.ler(calendario(evento("DTSTART;VALUE=DATE:20261110")), fuso).eventos.single()
        assertNull(d.instante)
    }
    @Test fun desdobraLinhasEDesescapaTexto() {
        val s = calendario(evento("DTSTART:20261110T010000Z", extra = "DESCRIPTION:Linha\\nOutra\\; \\n\r\n continuação"))
        assertTrue(LeitorIcs.ler(s, fuso).eventos.single().descricao.contains("continuação"))
    }
    @Test fun rejeitaRecorrenciaFlutuanteFusoDesconhecidoEDataInvalida() {
        val s = listOf(
            evento("DTSTART:20261110T100000", "flutuante"),
            evento("DTSTART;TZID=NaoExiste:20261110T100000", "zona"),
            evento("DTSTART;VALUE=DATE:20260230", "data"),
            evento("DTSTART:20261110T100000Z", "serie", "RRULE:FREQ=DAILY"),
            evento("DTSTART;TZID=Europe/Berlin:20260329T023000", "inexistente"),
            evento("DTSTART;TZID=Europe/Berlin:20261025T023000", "ambiguo")
        ).joinToString("\r\n")
        val resultado = LeitorIcs.ler(calendario(s), fuso)
        assertTrue(resultado.eventos.isEmpty()); assertEquals(6, resultado.ignorados)
    }
    @Test fun uidsRepetidosECancelamentoNaoGeramEscolhaSilenciosa() {
        val s = evento("DTSTART:20261110T100000Z") + "\r\n" + evento("DTSTART:20261111T100000Z") + "\r\n" +
            evento("DTSTART:20261111T100000Z", "cancelado", "STATUS:CANCELLED")
        assertEquals(3, LeitorIcs.ler(calendario(s), fuso).ignorados)
    }
    @Test fun prazoExatoMudaStatusNoInstanteMasManualContinuaPorDia() {
        val hoje = LocalDate.of(2026,11,10)
        val instante = Instant.parse("2026-11-10T21:00:00Z").toEpochMilli()
        val base = Atividade(titulo="Teste", disciplinaId=1, tipo=TipoAtividade.TAREFA,
            prioridade=PrioridadeAtividade.MEDIA, prazoEpochDay=hoje.toEpochDay())
        assertFalse(base.estaAtrasada(hoje, instante + 1))
        val exata = base.copy(prazoExatoEpochMillis = instante)
        assertFalse(exata.estaAtrasada(hoje, instante - 1)); assertTrue(exata.estaAtrasada(hoje, instante))
        val aviso = PlanoAutomatico.horario(exata, EtapaAutomatica.ATRASO, fuso)
        assertEquals(Instant.parse("2026-11-11T12:00:00Z").toEpochMilli(), aviso)
        assertTrue(aviso > instante)
    }
    @Test fun avisoDepoisDePrazoAsOitoPodeSerNasNoveDoMesmoDia() {
        val exato = Instant.parse("2026-11-10T11:00:00Z") // 08h em São Paulo
        val atividade = Atividade(titulo="Exata", disciplinaId=1, tipo=TipoAtividade.TAREFA,
            prioridade=PrioridadeAtividade.MEDIA, prazoEpochDay=LocalDate.of(2026,11,10).toEpochDay(),
            prazoExatoEpochMillis=exato.toEpochMilli())
        val horario = PlanoAutomatico.horario(atividade, EtapaAutomatica.ATRASO, fuso)
        assertEquals(Instant.parse("2026-11-10T12:00:00Z").toEpochMilli(), horario)
        assertFalse(PlanoAutomatico.pertinente(atividade, EtapaAutomatica.ATRASO, exato.minusSeconds(1), fuso))
        assertTrue(PlanoAutomatico.pertinente(atividade, EtapaAutomatica.ATRASO, Instant.ofEpochMilli(horario), fuso))
    }
}
