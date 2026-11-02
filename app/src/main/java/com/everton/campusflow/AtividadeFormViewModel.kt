package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.everton.campusflow.data.*
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.everton.campusflow.lembretes.Lembretes

data class AtividadeFormState(
    val titulo: String = "",
    val descricao: String = "",
    val disciplinaId: Long? = null,
    val tipo: TipoAtividade = TipoAtividade.TAREFA,
    val prioridade: PrioridadeAtividade = PrioridadeAtividade.MEDIA,
    val prazo: Long? = null,
    val disciplinas: List<Disciplina> = emptyList(),
    val pronto: Boolean = false,
    val disciplinasCarregadas: Boolean = false,
    val ocupado: Boolean = false,
    val inexistente: Boolean = false,
    val erro: Int? = null,
    val finalizado: Boolean = false,
    val prazoExato: Long? = null,
    val automatico: Boolean = true,
    val lembrar: Boolean = false,
    val lembrete: Long? = null,
    val aviso: Int? = null,
)

class AtividadeFormViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val db = CampusFlowDatabase.getInstance(application)
    private val dao = db.atividadeDao()
    private val id = saved.get<Long>(AtividadeFormActivity.EXTRA_ATIVIDADE_ID)
    val editando = id != null
    var tituloOriginal = ""
        private set
    private var lembreteOriginal: Long? = null
    private val _estado = MutableStateFlow(AtividadeFormState())
    val estado = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                if (id != null) {
                    val original = if (id > 0) dao.buscarPorId(id) else null
                    if (original == null) {
                        _estado.value = _estado.value.copy(inexistente = true, erro = R.string.activity_not_found)
                        return@launch
                    }
                    tituloOriginal = original.titulo
                    lembreteOriginal = original.lembreteEpochMillis
                    if (!saved.contains("rascunho")) {
                        saved["titulo"] = original.titulo
                        saved["descricao"] = original.descricao
                        saved["disciplina"] = original.disciplinaId
                        saved["tipo"] = original.tipo.name
                        saved["prioridade"] = original.prioridade.name
                        saved["prazo"] = original.prazoEpochDay
                        saved["prazoExato"] = original.prazoExatoEpochMillis
                        saved["automatico"] = original.lembretesAutomaticos || original.lembreteEpochMillis == null
                        saved["lembrar"] = original.lembretesAutomaticos || original.lembreteEpochMillis != null
                        saved["lembrete"] = original.lembreteEpochMillis
                    }
                }
                _estado.value = _estado.value.copy(
                    titulo = saved["titulo"] ?: "", descricao = saved["descricao"] ?: "",
                    disciplinaId = saved["disciplina"], prazo = saved["prazo"], prazoExato = saved["prazoExato"],
                    tipo = TipoAtividade.valueOf(saved["tipo"] ?: TipoAtividade.TAREFA.name),
                    prioridade = PrioridadeAtividade.valueOf(saved["prioridade"] ?: PrioridadeAtividade.MEDIA.name),
                    pronto = true,
                    automatico = saved["automatico"] ?: true,
                    lembrar = saved["lembrar"] ?: false, lembrete = saved["lembrete"],
                )
                db.disciplinaDao().observarTodas().catch {
                    if (it is CancellationException) throw it
                    _estado.value = _estado.value.copy(disciplinasCarregadas = false, erro = R.string.activity_load_error)
                }.collect { disciplinas ->
                    _estado.value = _estado.value.copy(disciplinas = disciplinas, disciplinasCarregadas = true)
                }
            } catch (cancelamento: CancellationException) {
                throw cancelamento
            } catch (_: Exception) {
                _estado.value = _estado.value.copy(erro = R.string.activity_load_error)
            }
        }
    }

    fun rascunho(
        titulo: String = estado.value.titulo, descricao: String = estado.value.descricao,
        disciplinaId: Long? = estado.value.disciplinaId, tipo: TipoAtividade = estado.value.tipo,
        prioridade: PrioridadeAtividade = estado.value.prioridade, prazo: Long? = estado.value.prazo,
        lembrar: Boolean = estado.value.lembrar, lembrete: Long? = estado.value.lembrete,
        automatico: Boolean = estado.value.automatico,
    ) {
        if (!estado.value.pronto || estado.value.ocupado || estado.value.finalizado) return
        saved["rascunho"] = true
        saved["titulo"] = titulo
        saved["descricao"] = descricao
        saved["disciplina"] = disciplinaId
        saved["tipo"] = tipo.name
        saved["prioridade"] = prioridade.name
        saved["prazo"] = if (estado.value.prazoExato != null) estado.value.prazo else prazo
        saved["automatico"] = automatico
        saved["lembrar"] = lembrar
        saved["lembrete"] = lembrete
        _estado.value = estado.value.copy(titulo = titulo, descricao = descricao,
            disciplinaId = disciplinaId, tipo = tipo, prioridade = prioridade, prazo = saved["prazo"],
            automatico = automatico, lembrar = lembrar, lembrete = lembrete, erro = null)
    }

    fun salvar() {
        val draft = estado.value
        if (!draft.pronto || draft.ocupado || draft.finalizado || draft.inexistente) return
        val disciplinaId = draft.disciplinaId
        val prazo = draft.prazo
        val erro = when {
            draft.titulo.isBlank() -> R.string.activity_title_required
            disciplinaId == null -> R.string.activity_discipline_required
            prazo == null -> R.string.activity_due_required
            !draft.automatico && draft.lembrar && draft.lembrete == null -> R.string.reminder_time_required
            !draft.automatico && draft.lembrar && draft.lembrete!! <= System.currentTimeMillis() &&
                draft.lembrete != lembreteOriginal -> R.string.reminder_future_required
            else -> null
        }
        if (erro != null) { _estado.value = draft.copy(erro = erro); return }
        _estado.value = draft.copy(ocupado = true, erro = null)
        viewModelScope.launch {
            try {
                LocalDate.ofEpochDay(prazo!!)
                var idSalvo = id ?: 0L
                val resultado = db.withTransaction {
                    // Revalidação e gravação atômicas, além da proteção da chave estrangeira.
                    if (db.disciplinaDao().buscarPorId(disciplinaId!!) == null) {
                        return@withTransaction R.string.activity_discipline_required
                    }
                    val atual = id?.let { dao.buscarPorId(it) }
                    if (id != null && atual == null) return@withTransaction R.string.activity_not_found
                    val auto = draft.lembrar && draft.automatico
                    // A data importada não é editável neste formulário. Preserva um prazo
                    // sincronizado enquanto o rascunho estava aberto, incluindo seu dia local.
                    val prazoSalvo = atual?.prazoExatoEpochMillis?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                    } ?: prazo
                    val mesmoPrazo = atual?.prazoEpochDay == prazoSalvo
                    val agora = java.time.Instant.now()
                    val atividade = Atividade(id = id ?: 0, titulo = draft.titulo.trim(),
                        descricao = draft.descricao.trim(), disciplinaId = disciplinaId,
                        tipo = draft.tipo, prioridade = draft.prioridade, prazoEpochDay = prazoSalvo,
                        concluida = atual?.concluida ?: false,
                        prazoExatoEpochMillis = atual?.prazoExatoEpochMillis,
                        lembretesAutomaticos = auto,
                        avisoAtrasoDia = if (auto) {
                            if (mesmoPrazo && atual?.lembretesAutomaticos == true) atual.avisoAtrasoDia
                            else atual?.prazoExatoEpochMillis?.let {
                                com.everton.campusflow.importacao.PrazosIcs.proximasNove(maxOf(agora, java.time.Instant.ofEpochMilli(it)), java.time.ZoneId.systemDefault())
                            } ?: com.everton.campusflow.lembretes.PlanoAutomatico.diaAtraso(prazoSalvo, agora, java.time.ZoneId.systemDefault())
                        } else null,
                        lembretesAtivadosEm = if (auto) {
                            if (mesmoPrazo && atual?.lembretesAutomaticos == true) atual.lembretesAtivadosEm
                            else agora.toEpochMilli()
                        } else null,
                        lembreteEpochMillis = if (draft.lembrar && !draft.automatico) draft.lembrete else null,
                        lembreteEnviado = if (draft.lembrar && draft.lembrete == atual?.lembreteEpochMillis)
                            atual?.lembreteEnviado ?: false else false)
                    if (id == null) idSalvo = dao.inserir(atividade) else check(dao.atualizar(atividade) == 1)
                    null
                }
                var aviso: Int? = null
                if (resultado == null) {
                    try { Lembretes.sincronizar(getApplication(), idSalvo)
                    } catch (cancelamento: CancellationException) { throw cancelamento
                    } catch (_: Exception) { aviso = R.string.reminder_schedule_error }
                }
                _estado.value = estado.value.copy(ocupado = false, finalizado = resultado == null,
                    erro = resultado, aviso = aviso, inexistente = resultado == R.string.activity_not_found)
            } catch (cancelamento: CancellationException) { throw cancelamento
            } catch (_: Exception) {
                _estado.value = estado.value.copy(ocupado = false, erro = R.string.activity_save_error)
            }
        }
    }

    fun excluir() {
        val atividadeId = id ?: return
        if (!estado.value.pronto || estado.value.ocupado || estado.value.finalizado || estado.value.inexistente) return
        _estado.value = estado.value.copy(ocupado = true, erro = null)
        viewModelScope.launch {
            try {
                val excluida = dao.excluirPorId(atividadeId) == 1
                var aviso: Int? = null
                try { Lembretes.sincronizar(getApplication(), atividadeId)
                } catch (cancelamento: CancellationException) { throw cancelamento
                } catch (_: Exception) { aviso = R.string.reminder_schedule_error }
                _estado.value = estado.value.copy(ocupado = false, finalizado = excluida,
                    inexistente = !excluida, aviso = aviso, erro = if (excluida) null else R.string.activity_not_found)
            } catch (cancelamento: CancellationException) { throw cancelamento
            } catch (_: Exception) {
                _estado.value = estado.value.copy(ocupado = false, erro = R.string.activity_delete_error)
            }
        }
    }
}
