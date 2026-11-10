package com.everton.campusflow.lembretes

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface HorarioSelecionado {
    data class Valido(val epochMillis: Long) : HorarioSelecionado
    data object Passado : HorarioSelecionado
    data object InexistenteNoFuso : HorarioSelecionado
}

object HorarioLembrete {
    fun validar(local: LocalDateTime, fuso: ZoneId, agora: Instant): HorarioSelecionado {
        val zoned = local.atZone(fuso)
        if (zoned.toLocalDateTime() != local) return HorarioSelecionado.InexistenteNoFuso
        val instante = zoned.toInstant()
        return if (instante > agora) HorarioSelecionado.Valido(instante.toEpochMilli())
        else HorarioSelecionado.Passado
    }
}
