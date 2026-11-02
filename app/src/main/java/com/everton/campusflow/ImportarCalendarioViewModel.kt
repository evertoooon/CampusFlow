package com.everton.campusflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.everton.campusflow.data.*
import com.everton.campusflow.importacao.*
import com.everton.campusflow.lembretes.Lembretes
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ImportarState(val eventos: List<EventoIcs> = emptyList(), val ignorados: Int = 0,
    val disciplinas: List<Disciplina> = emptyList(), val selecoes: Map<String, Long> = emptyMap(),
    val lembrar: Boolean = false, val ocupado: Boolean = false, val mensagem: Int? = null,
    val resultado: ResultadoImportacao? = null, val aviso: Boolean = false)

class ImportarCalendarioViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val db = CampusFlowDatabase.getInstance(app)
    private val _estado = MutableStateFlow(ImportarState(lembrar = saved["lembrar"] ?: false))
    val estado = _estado.asStateFlow()
    private val pasta = File(app.cacheDir, "previas-ics").apply { mkdirs() }
    init {
        viewModelScope.launch {
            db.disciplinaDao().observarTodas().catch {
                if (it is CancellationException) throw it
                _estado.value = estado.value.copy(mensagem = R.string.disciplines_load_error)
            }.collect { _estado.value = estado.value.copy(disciplinas = it) }
        }
        val nome = saved.get<String>("arquivo")
        if (nome != null && nome.matches(Regex("[a-f0-9-]+\\.ics"))) {
            _estado.value = estado.value.copy(ocupado = true)
            viewModelScope.launch {
            try {
                val calendario = withContext(Dispatchers.IO) { LeitorIcs.ler(File(pasta, nome).readText(), ZoneId.systemDefault()) }
                val selecoes = calendario.eventos.mapNotNull { evento ->
                    saved.get<Long>("disciplina-${evento.uid}")?.let { evento.uid to it }
                }.toMap()
                _estado.value = estado.value.copy(eventos = calendario.eventos, ignorados = calendario.ignorados, selecoes = selecoes, ocupado = false)
            } catch (cancelamento: CancellationException) { throw cancelamento
            } catch (_: Exception) { _estado.value = estado.value.copy(mensagem = R.string.import_read_error, ocupado = false) }
            }
        }
    }
    fun carregar(uri: Uri) {
        if (estado.value.ocupado || estado.value.resultado != null) return
        estado.value.eventos.forEach { saved.remove<Long>("disciplina-${it.uid}") }
        val arquivoAnterior = saved.remove<String>("arquivo")
        _estado.value = estado.value.copy(ocupado = true, mensagem = null, eventos = emptyList(), selecoes = emptyMap(), ignorados = 0)
        viewModelScope.launch {
            try {
                val (nome, calendario) = withContext(Dispatchers.IO) {
                    arquivoAnterior?.let { File(pasta, it).delete() }
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val lidos = input.read(buffer)
                            if (lidos < 0) break
                            total += lidos; require(total <= 1_048_576)
                            output.write(buffer, 0, lidos)
                        }
                        output.toByteArray()
                    } ?: error("Arquivo indisponível")
                    val texto = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    val calendario = LeitorIcs.ler(texto, ZoneId.systemDefault())
                    val nome = "${UUID.randomUUID()}.ics"
                    File(pasta, nome).writeText(texto)
                    nome to calendario
                }
                (estado.value.eventos + calendario.eventos).forEach { saved.remove<Long>("disciplina-${it.uid}") }
                saved.get<String>("arquivo")?.let { File(pasta, it).delete() }
                saved["arquivo"] = nome
                _estado.value = estado.value.copy(eventos = calendario.eventos, ignorados = calendario.ignorados,
                    selecoes = emptyMap(), ocupado = false, mensagem = if (calendario.eventos.isEmpty()) R.string.import_empty else null)
            } catch (cancelamento: CancellationException) { throw cancelamento
            } catch (_: Exception) { _estado.value = estado.value.copy(ocupado = false, mensagem = R.string.import_read_error) }
        }
    }
    fun selecionar(uid: String, disciplina: Long?) {
        if (estado.value.ocupado || estado.value.resultado != null) return
        if (disciplina != null && uid !in estado.value.selecoes && estado.value.selecoes.size >= 3) {
            _estado.value = estado.value.copy(mensagem = R.string.import_limit); return
        }
        saved["disciplina-$uid"] = disciplina
        _estado.value = estado.value.copy(selecoes = estado.value.selecoes.toMutableMap().apply {
            if (disciplina == null) remove(uid) else put(uid, disciplina)
        }, mensagem = null)
    }
    fun lembrar(ativo: Boolean) {
        if (estado.value.ocupado || estado.value.resultado != null) return
        saved["lembrar"] = ativo
        _estado.value = estado.value.copy(lembrar = ativo)
    }
    fun importar() {
        val draft = estado.value
        if (draft.ocupado || draft.resultado != null) return
        if (draft.selecoes.isEmpty()) { _estado.value = draft.copy(mensagem = R.string.import_choose); return }
        _estado.value = draft.copy(ocupado = true, mensagem = null)
        viewModelScope.launch {
            try {
                val escolhas = draft.eventos.filter { it.uid in draft.selecoes }.map { EscolhaIcs(it, draft.selecoes.getValue(it.uid)) }
                val resultado = ImportadorIcs(db).importar(escolhas, draft.lembrar, Instant.now(), ZoneId.systemDefault())
                // O resultado do banco é definitivo mesmo que o agendamento falhe.
                _estado.value = estado.value.copy(resultado = resultado)
                var aviso = false
                for (id in resultado.ids) {
                    try { Lembretes.sincronizar(getApplication(), id)
                    } catch (cancelamento: CancellationException) { throw cancelamento
                    } catch (_: Exception) { aviso = true }
                }
                saved.get<String>("arquivo")?.let { File(pasta, it).delete() }
                saved.remove<String>("arquivo")
                _estado.value = estado.value.copy(ocupado = false, aviso = aviso)
            } catch (cancelamento: CancellationException) { throw cancelamento
            } catch (_: Exception) { _estado.value = estado.value.copy(ocupado = false, mensagem = R.string.import_save_error) }
        }
    }
    override fun onCleared() {
        saved.get<String>("arquivo")?.let { File(pasta, it).delete() }
        super.onCleared()
    }

}
