package com.everton.campusflow.prazos

import java.time.*
import java.time.format.DateTimeFormatter

object Prazos {
    fun formatar(instante: Long): String = Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss VV"))
    fun proximasNove(instante: Instant, fuso: ZoneId): Long {
        val hoje = instante.atZone(fuso).toLocalDate()
        return (if (hoje.atTime(9, 0).atZone(fuso).toInstant() > instante) hoje else hoje.plusDays(1)).toEpochDay()
    }
}
