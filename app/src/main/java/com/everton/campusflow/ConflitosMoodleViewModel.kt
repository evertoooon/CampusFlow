package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ItemConflitoMoodle(val controle: ControleMoodle, val atividade: Atividade, val disciplina: String)
data class ConflitosMoodleState(val itens: List<ItemConflitoMoodle> = emptyList(), val ocupado: Boolean = false,
    val erro: Boolean = false, val mensagem: Int? = null)

class ConflitosMoodleViewModel(app: Application) : AndroidViewModel(app) {
    private val db=CampusFlowDatabase.getInstance(app)
    private val _estado=MutableStateFlow(ConflitosMoodleState())
    val estado=_estado.asStateFlow()
    init { viewModelScope.launch {
        combine(db.controleMoodleDao().observarPendentes(),db.atividadeDao().observarTodasComDisciplina()) { controles,lista ->
            controles.mapNotNull { c ->
                val a=db.atividadeDao().buscarPorId(c.atividadeId) ?: return@mapNotNull null
                val d=lista.firstOrNull { it.id==a.id }?.disciplinaNome.orEmpty()
                ItemConflitoMoodle(c,a,d)
            }
        }.catch { _estado.update { it.copy(erro=true) } }
            .collect { itens -> _estado.update { it.copy(itens=itens,erro=false) } }
    } }
    fun resolver(item: ItemConflitoMoodle,campo: CampoMoodle,usarMoodle: Boolean) {
        if(estado.value.ocupado) return
        _estado.update { it.copy(ocupado=true,mensagem=null) }
        viewModelScope.launch {
            try {
                val r=ResolverConflitoMoodle(db).resolver(item.controle,item.atividade,campo,usarMoodle)
                var mensagem=when(r) {
                    ResultadoResolucao.APLICADA -> R.string.moodle_conflict_resolved
                    ResultadoResolucao.DESATUALIZADA,ResultadoResolucao.INEXISTENTE -> R.string.moodle_conflict_stale
                    ResultadoResolucao.PRAZO_OBRIGATORIO -> R.string.moodle_conflict_due_required
                }
                if(r==ResultadoResolucao.APLICADA) try {
                    com.everton.campusflow.lembretes.Lembretes.sincronizar(getApplication(),item.atividade.id)
                } catch(c:CancellationException) { throw c } catch(_:Exception) { mensagem=R.string.reminder_schedule_error }
                _estado.update { it.copy(ocupado=false,mensagem=mensagem) }
            } catch(c:CancellationException) { throw c
            } catch(_:Exception) { _estado.update { it.copy(ocupado=false,mensagem=R.string.moodle_conflict_error) } }
        }
    }
}
