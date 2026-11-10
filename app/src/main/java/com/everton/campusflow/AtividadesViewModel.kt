package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.everton.campusflow.data.AtividadeComDisciplina
import com.everton.campusflow.data.AtividadeDao
import com.everton.campusflow.data.CampusFlowDatabase
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.everton.campusflow.lembretes.Lembretes

data class AtividadesUiState(
    val carregando: Boolean = true,
    val erro: Boolean = false,
    val pendentes: List<AtividadeComDisciplina> = emptyList(),
    val concluidas: List<AtividadeComDisciplina> = emptyList(),
    val salvandoIds: Set<Long> = emptySet(),
    val erroAtualizacaoId: Long? = null,
    val avisoLembrete: Boolean = false,
    val agoraEpochMillis: Long = System.currentTimeMillis(),
    val hojeEpochDay: Long = LocalDate.now().toEpochDay(),
)

class AtividadesViewModel internal constructor(
    application: Application,
    private val dao: AtividadeDao,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        CampusFlowDatabase.getInstance(application).atividadeDao(),
    )

    private val _estado = MutableStateFlow(AtividadesUiState())
    val estado = _estado.asStateFlow()

    private var jobConsulta: Job? = null

    init {
        carregarAtividades()
    }

    fun carregarAtividades() {
        jobConsulta?.cancel()
        _estado.value = _estado.value.copy(carregando = true, erro = false)
        jobConsulta = viewModelScope.launch {
            dao.observarTodasComDisciplina()
                .catch {
                    if (it is CancellationException) throw it
                    _estado.value = _estado.value.copy(carregando = false, erro = true)
                }
                .collect { lista ->
                    val pendentes = lista.filter { !it.concluida }
                    val concluidas = lista.filter { it.concluida }
                    _estado.value = _estado.value.copy(
                        carregando = false,
                        erro = false,
                        pendentes = pendentes,
                        concluidas = concluidas,
                    )
                }
        }
    }

    fun atualizarHoje() {
        val novoHoje = LocalDate.now().toEpochDay()
        _estado.value = _estado.value.copy(hojeEpochDay = novoHoje, agoraEpochMillis = System.currentTimeMillis())
    }

    fun alternarConcluida(atividade: AtividadeComDisciplina) {
        val id = atividade.id
        val novoStatus = !atividade.concluida
        val atuaisSalvando = _estado.value.salvandoIds

        if (atuaisSalvando.contains(id)) return

        _estado.value = _estado.value.copy(
            salvandoIds = atuaisSalvando + id,
            erroAtualizacaoId = null,
        )

        viewModelScope.launch {
            try {
                val linhasAfetadas = dao.atualizarConcluida(id, novoStatus)
                if (linhasAfetadas == 1) {
                    try { Lembretes.sincronizar(getApplication(), id)
                    } catch (cancelamento: CancellationException) { throw cancelamento
                    } catch (_: Exception) { _estado.value = _estado.value.copy(avisoLembrete = true) }
                }
                if (linhasAfetadas != 1) {
                    _estado.value = _estado.value.copy(
                        salvandoIds = _estado.value.salvandoIds - id,
                        erroAtualizacaoId = id,
                    )
                } else {
                    // Mantém o controle bloqueado até a lista refletir a gravação.
                    withTimeout(5_000) {
                        estado.first { state ->
                            (state.pendentes + state.concluidas).any {
                                it.id == id && it.concluida == novoStatus
                            }
                        }
                    }
                    _estado.value = _estado.value.copy(
                        salvandoIds = _estado.value.salvandoIds - id,
                    )
                }
            } catch (cancelamento: CancellationException) {
                if (cancelamento !is TimeoutCancellationException) throw cancelamento
                _estado.value = _estado.value.copy(erroAtualizacaoId = id)
            } catch (_: Exception) {
                _estado.value = _estado.value.copy(
                    salvandoIds = _estado.value.salvandoIds - id,
                    erroAtualizacaoId = id,
                )
            } finally {
                _estado.value = _estado.value.copy(salvandoIds = _estado.value.salvandoIds - id)
            }
        }
    }

    fun limparErroAtualizacao() {
        _estado.value = _estado.value.copy(erroAtualizacaoId = null, avisoLembrete = false)
    }
}
