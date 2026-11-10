package com.everton.campusflow

import com.everton.campusflow.lembretes.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HorarioLembreteTest {
    private val agora = Instant.parse("2026-10-27T21:08:57Z")
    private fun validar(horario: String, fuso: String = "GMT", instante: Instant = agora) =
        HorarioLembrete.validar(LocalDateTime.parse(horario), ZoneId.of(fuso), instante)

    @Test fun proximoMinutoEValidoMesmoComApenasTresSegundosDeAntecedencia() {
        assertEquals(HorarioSelecionado.Valido(Instant.parse("2026-10-27T21:09:00Z").toEpochMilli()),
            validar("2026-10-27T21:09"))
    }
    @Test fun minutoAtualEPassadoPorqueOSeletorNaoPossuiSegundos() {
        assertEquals(HorarioSelecionado.Passado, validar("2026-10-27T21:08"))
    }
    @Test fun horaDeSaoPauloNaoPodeSerUsadaComoSeFosseHoraGmt() {
        assertEquals(HorarioSelecionado.Passado, validar("2026-10-27T18:10"))
        assertEquals(HorarioSelecionado.Valido(Instant.parse("2026-10-27T21:10:00Z").toEpochMilli()),
            validar("2026-10-27T18:10", "America/Sao_Paulo"))
    }
    @Test fun futuroNaViradaDoDiaContinuaValido() {
        assertTrue(validar("2026-10-28T00:00", instante = Instant.parse("2026-10-27T23:59:58Z")) is HorarioSelecionado.Valido)
    }
    @Test fun horarioInexistenteNaMudancaDeFusoTemErroEspecifico() {
        assertEquals(HorarioSelecionado.InexistenteNoFuso, validar("2026-03-29T02:30", "Europe/Berlin",
            Instant.parse("2026-03-28T00:00:00Z")))
    }
}
