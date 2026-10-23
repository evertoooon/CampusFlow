package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.everton.campusflow.data.CampusFlowDatabase
import com.everton.campusflow.data.Disciplina
import com.everton.campusflow.data.DisciplinaDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DisciplinaSaveState(
    val nome: String = "",
    val professor: String = "",
    val carregando: Boolean = false,
    val pronto: Boolean = false,
    val inexistente: Boolean = false,
    val erroCarga: Boolean = false,
    val salvando: Boolean = false,
    val salva: Boolean = false,
    val erro: Boolean = false,
    val excluida: Boolean = false,
    val excluindo: Boolean = false,
    val erroExclusao: Boolean = false,
)

class DisciplinaFormViewModel internal constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val dao: DisciplinaDao,
) : AndroidViewModel(application) {
    constructor(application: Application, savedState: SavedStateHandle) : this(
        application, savedState, CampusFlowDatabase.getInstance(application).disciplinaDao(),
    )

    private val disciplinaId = savedState.get<Long>(DisciplinaFormActivity.EXTRA_DISCIPLINA_ID)
    val editando = disciplinaId != null
    var nomeOriginal: String = ""
        private set
    private val _estado = MutableStateFlow(DisciplinaSaveState())
    val estado = _estado.asStateFlow()

    init {
        if (disciplinaId == null) {
            publicarRascunho()
        } else if (disciplinaId <= 0) {
            _estado.value = DisciplinaSaveState(inexistente = true)
        } else {
            _estado.value = DisciplinaSaveState(carregando = true)
            viewModelScope.launch {
                try {
                    val disciplina = dao.buscarPorId(disciplinaId)
                    if (disciplina == null) {
                        _estado.value = DisciplinaSaveState(inexistente = true)
                    } else {
                        nomeOriginal = disciplina.nome
                        // Um rascunho restaurado tem prioridade sobre os dados do banco.
                        if (!savedState.contains("nome")) {
                            savedState["nome"] = disciplina.nome
                            savedState["professor"] = disciplina.professor
                        }
                        publicarRascunho()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _estado.value = DisciplinaSaveState(erroCarga = true)
                }
            }
        }
    }

    private fun publicarRascunho() {
        _estado.value = DisciplinaSaveState(
            nome = savedState["nome"] ?: "",
            professor = savedState["professor"] ?: "",
            pronto = true,
        )
    }

    fun atualizarRascunho(nome: String, professor: String) {
        val atual = _estado.value
        if (!atual.pronto || atual.salvando || atual.salva || atual.excluida) return
        savedState["nome"] = nome
        savedState["professor"] = professor
        _estado.value = atual.copy(nome = nome, professor = professor)
    }

    fun salvar(nome: String, professor: String) {
        // Evita inserções duplicadas por toques repetidos ou recriação da tela.
        val atual = _estado.value
        if (!atual.pronto || atual.salvando || atual.salva || atual.excluida || nome.isBlank()) return
        atualizarRascunho(nome, professor)
        _estado.value = _estado.value.copy(salvando = true, erro = false, erroExclusao = false)
        viewModelScope.launch {
            try {
                val disciplina = Disciplina(
                    id = disciplinaId ?: 0,
                    nome = nome.trim(),
                    professor = professor.trim(),
                )
                if (disciplinaId == null) {
                    dao.inserir(disciplina)
                } else if (dao.atualizar(disciplina) != 1) {
                    _estado.value = _estado.value.copy(
                        salvando = false, pronto = false, inexistente = true,
                    )
                    return@launch
                }
                _estado.value = _estado.value.copy(salvando = false, salva = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _estado.value = _estado.value.copy(salvando = false, erro = true)
            }
        }
    }

    fun excluir() {
        val id = disciplinaId ?: return
        val atual = _estado.value
        if (id <= 0 || !atual.pronto || atual.salvando || atual.salva || atual.excluida) return
        _estado.value = atual.copy(salvando = true, excluindo = true, erro = false, erroExclusao = false)
        viewModelScope.launch {
            try {
                if (dao.excluirPorId(id) != 1) {
                    _estado.value = _estado.value.copy(
                        salvando = false, excluindo = false, pronto = false, inexistente = true,
                    )
                    return@launch
                }
                _estado.value = _estado.value.copy(salvando = false, excluindo = false, excluida = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _estado.value = _estado.value.copy(
                    salvando = false, excluindo = false, erroExclusao = true,
                )
            }
        }
    }
}
