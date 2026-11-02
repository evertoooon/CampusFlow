package com.everton.campusflow.importacao

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class EventoIcs(val uid: String, val titulo: String, val descricao: String,
    val dia: Long, val instante: Long?, val fuso: String?)
data class CalendarioIcs(val eventos: List<EventoIcs>, val ignorados: Int)

// Subconjunto explícito da RFC 5545: não expande recorrências nem adivinha fusos.
object LeitorIcs {
    private data class Campo(val nome: String, val parametros: Map<String, String>, val valor: String)
    private val data = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
    private val hora = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT)
    private fun campo(linha: String): Campo {
        var aspas = false
        val separador = linha.indexOfFirst { c ->
            if (c == '"') aspas = !aspas
            c == ':' && !aspas
        }
        require(separador > 0)
        val partes = linha.substring(0, separador).split(';')
        val parametros = partes.drop(1).associate {
            val p = it.split('=', limit = 2); require(p.size == 2)
            p[0].uppercase() to p[1].trim('"')
        }
        return Campo(partes[0].uppercase(), parametros, linha.substring(separador + 1))
    }
    private fun texto(valor: String): String = buildString {
        var i = 0
        while (i < valor.length) {
            val c = valor[i++]
            if (c != '\\') append(c) else {
                require(i < valor.length)
                when (val proximo = valor[i++]) {
                    'n', 'N' -> append('\n')
                    '\\', ',', ';' -> append(proximo)
                    else -> throw IllegalArgumentException("Escape não suportado")
                }
            }
        }
    }
    fun ler(conteudo: String, fusoExibicao: ZoneId): CalendarioIcs {
        require(conteudo.length <= 1_048_576)
        val desdobradas = mutableListOf<StringBuilder>()
        conteudo.removePrefix("\uFEFF").replace("\r\n", "\n").split('\n').forEach {
            if ((it.startsWith(' ') || it.startsWith('\t')) && desdobradas.isNotEmpty())
                desdobradas.last().append(it.drop(1))
            else if (it.isNotEmpty()) desdobradas += StringBuilder(it.removeSuffix("\r"))
        }
        val linhas = desdobradas.map { it.toString() }
        require(linhas.firstOrNull() == "BEGIN:VCALENDAR" && linhas.lastOrNull() == "END:VCALENDAR")
        val eventos = mutableListOf<EventoIcs>()
        var ignorados = 0
        var campos: MutableList<String>? = null
        var profundidade = 0
        for (linha in linhas.drop(1).dropLast(1)) {
            if (linha == "BEGIN:VEVENT") { require(campos == null); campos = mutableListOf(); profundidade = 0 }
            else if (linha == "END:VEVENT") {
                val bloco = campos ?: throw IllegalArgumentException()
                require(eventos.size + ignorados < 200)
                val evento = runCatching { require(profundidade == 0); converter(bloco, fusoExibicao) }.getOrNull()
                if (evento == null) ignorados++ else eventos += evento
                campos = null
            } else if (campos != null) {
                if (linha.startsWith("BEGIN:")) profundidade++
                else if (linha.startsWith("END:")) profundidade--
                else if (profundidade == 0) campos.add(linha)
                require(profundidade >= 0)
            }
        }
        require(campos == null)
        // UIDs repetidos podem ser revisões conflitantes. Não escolher uma silenciosamente.
        val repetidos = eventos.groupingBy { it.uid }.eachCount().filterValues { it > 1 }.keys
        return CalendarioIcs(eventos.filter { it.uid !in repetidos }, ignorados + eventos.count { it.uid in repetidos })
    }
    private fun converter(linhas: List<String>, fuso: ZoneId): EventoIcs {
        val campos = linhas.map(::campo)
        require(campos.none { it.nome in setOf("RRULE", "RDATE", "EXDATE", "RECURRENCE-ID") })
        require(campos.none { it.nome == "STATUS" && it.valor == "CANCELLED" })
        fun unico(nome: String): Campo = campos.filter { it.nome == nome }.single()
        val uid = texto(unico("UID").valor).trim().also { require(it.isNotEmpty() && it.length <= 512) }
        val titulo = texto(unico("SUMMARY").valor).trim().also { require(it.isNotEmpty() && it.length <= 300) }
        val descricao = campos.filter { it.nome == "DESCRIPTION" }.singleOrNull()?.valor?.let(::texto).orEmpty()
        require(descricao.length <= 16_000)
        val inicio = unico("DTSTART")
        if (inicio.parametros["VALUE"] == "DATE") {
            require(inicio.parametros["TZID"] == null)
            return EventoIcs(uid, titulo, descricao, LocalDate.parse(inicio.valor, data).toEpochDay(), null, null)
        }
        require(inicio.parametros["VALUE"] in listOf(null, "DATE-TIME"))
        val utc = inicio.valor.endsWith('Z')
        val zona = if (utc) ZoneOffset.UTC else ZoneId.of(inicio.parametros["TZID"] ?: error("Horário sem fuso"))
        require(!utc || inicio.parametros["TZID"] == null)
        val local = LocalDateTime.parse(inicio.valor.removeSuffix("Z"), hora)
        val offsets = zona.rules.getValidOffsets(local)
        require(offsets.size == 1) // Rejeita tanto hora inexistente quanto ambígua.
        val instante = local.toInstant(offsets.single())
        return EventoIcs(uid, titulo, descricao, instante.atZone(fuso).toLocalDate().toEpochDay(),
            instante.toEpochMilli(), zona.id)
    }
}
