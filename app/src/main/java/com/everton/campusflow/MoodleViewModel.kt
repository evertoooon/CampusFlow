package com.everton.campusflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.everton.campusflow.data.*
import com.everton.campusflow.moodle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

data class MoodleState(val ocupado: Boolean = false, val resultado: ResultadoMoodle? = null,
    val erro: MotivoMoodle? = null, val escolhas: Map<Long, Long?> = emptyMap(),
    val disciplinas: List<Disciplina> = emptyList(), val previa: List<PreviaCursoMoodle>? = null,
    val importado: ImportacaoMoodle? = null, val vinculados: Map<Long, Long> = emptyMap())

class MoodleViewModel @JvmOverloads constructor(app: Application,
    private val acesso: AcessoMoodle = MoodleUpf()) : AndroidViewModel(app) {
    private val _estado = MutableStateFlow(MoodleState())
    val estado = _estado.asStateFlow()
    private val db = CampusFlowDatabase.getInstance(app)
    // Somente memoria: nunca SavedStateHandle, Bundle ou StateFlow.
    private var token: String? = null
    private var site: SiteMoodle? = null
    private val professoresEditados = mutableMapOf<Long, String>()
    fun professor(p: PreviaCursoMoodle): String = professoresEditados[p.curso.id]
        ?: p.professores.nomes.joinToString("; ").takeIf { it.length <= 500 }.orEmpty()
    fun editarProfessor(cursoId: Long, texto: String) {
        if (!estado.value.ocupado) professoresEditados[cursoId] = texto.take(500)
    }
    init { viewModelScope.launch { db.disciplinaDao().observarTodas().collect { d ->
        _estado.update { it.copy(disciplinas = d) }
    } } }
    fun verificar(usuario: String, senha: String) {
        if (estado.value.ocupado) return
        token = null; site = null
        professoresEditados.clear()
        _estado.value = MoodleState(ocupado = true, disciplinas = estado.value.disciplinas)
        viewModelScope.launch {
            try {
                var credencial: String? = null; var info: SiteMoodle? = null
                val resultado = runInterruptible(Dispatchers.IO) {
                    VerificarMoodle(acesso) { t, s -> credencial = t; info = s }.verificar(usuario, senha)
                }
                token = credencial; site = info
                val vinculados = mutableMapOf<Long, Long>()
                (resultado.cursos as? ConsultaMoodle.Lida)?.dados.orEmpty().forEach { curso ->
                    db.cursoImportadoDao().buscar("${MoodleUpf.ORIGEM}/usuario/${info!!.usuarioId}", curso.id)?.let {
                        vinculados[curso.id] = it
                    }
                }
                _estado.update { it.copy(ocupado = false, resultado = resultado, vinculados = vinculados) }
            } catch (c: CancellationException) { throw c
            } catch (e: Exception) { falhar(e) }
        }
    }
    fun escolher(curso: Long, selecionar: Boolean, disciplina: Long? = null) {
        if (estado.value.ocupado || estado.value.importado != null) return
        _estado.update { it.copy(escolhas = it.escolhas.toMutableMap().apply {
            if (selecionar) put(curso, it.vinculados[curso] ?: disciplina) else remove(curso)
        }, previa = null, erro = null) }
    }
    fun consultar() {
        val atual = estado.value
        val t = token ?: return
        val s = site ?: return
        val cursos = (atual.resultado?.cursos as? ConsultaMoodle.Lida)?.dados ?: return
        if (atual.ocupado || atual.escolhas.size !in 1..20) return
        if ("mod_assign_get_assignments" !in s.funcoes) { falhar(FalhaMoodle(MotivoMoodle.ACESSO)); return }
        professoresEditados.clear()
        _estado.update { it.copy(ocupado = true, previa = null, erro = null) }
        viewModelScope.launch {
            try {
                val previa = runInterruptible(Dispatchers.IO) {
                    var total = 0
                    var estadosConsultados = 0
                    var consultarEnvios = "mod_assign_get_submission_status" in s.funcoes
                    cursos.filter { it.id in atual.escolhas }.map { curso ->
                        val consulta = acesso.consultarTarefas(t, curso.id)
                        val tarefas = consulta.tarefas.map { tarefa ->
                            if (!consultarEnvios || estadosConsultados >= 100) tarefa else {
                                estadosConsultados++
                                val envio = try { acesso.envio(t, tarefa) } catch (c: CancellationException) { throw c
                                } catch (_: Exception) { consultarEnvios = false; EnvioMoodle.DESCONHECIDO }
                                tarefa.copy(envio = envio)
                            }
                        }
                        total += tarefas.size
                        if (total > 500) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
                        val professores = if ("core_enrol_get_enrolled_users" !in s.funcoes) ProfessoresMoodle() else try {
                            acesso.professores(t, curso.id)
                        } catch (c: CancellationException) { throw c
                        } catch (_: Exception) { ProfessoresMoodle(estado = EstadoProfessores.FALHOU) }
                        PreviaCursoMoodle(curso, tarefas, atual.escolhas[curso.id], consulta.modulosRestritos, professores)
                    }
                }
                _estado.update { it.copy(ocupado = false, previa = previa) }
            } catch (c: CancellationException) { throw c
            } catch (e: Exception) { falhar(e) }
        }
    }
    fun importar(atualizarConclusoes: Boolean = false, acompanhar: Boolean = false) {
        val atual = estado.value; val previa = atual.previa?.map { it.copy(professor = professor(it)) } ?: return; val s = site ?: return
        if (atual.ocupado || atual.importado != null) return
        _estado.update { it.copy(ocupado = true, erro = null) }
        viewModelScope.launch {
            try {
                val resultado = ConexaoMoodle.sessaoMutex.withLock {
                    val salvo = ImportadorMoodle(db).importar(s.usuarioId, previa, atualizarConclusoes)
                    if (acompanhar) {
                        val t = token ?: error("Sessão indisponível")
                        try { withContext(Dispatchers.IO) {
                            ConexaoMoodle.conectarBloqueado(getApplication(), t, s.usuarioId, previa.map { it.curso })
                        } } catch (c: CancellationException) { throw c
                        } catch (_: Exception) { ConexaoMoodle.sinalizarFalhaConexao() }
                    }
                    salvo
                }
                // Reconciliar avisos de atividades concluídas após a transação, sem desfazer o resultado salvo.
                try { com.everton.campusflow.lembretes.Lembretes.reconciliar(getApplication()) }
                catch (c: CancellationException) { throw c } catch (_: Exception) { /* retomada normal também reconcilia */ }
                token = null; site = null
                _estado.update { it.copy(ocupado = false, importado = resultado) }
            } catch (c: CancellationException) { throw c
            } catch (e: Exception) { falhar(e) }
        }
    }
    private fun falhar(e: Exception) { _estado.update { it.copy(ocupado = false,
        erro = (e as? FalhaMoodle)?.motivo ?: MotivoMoodle.RESPOSTA) } }
    fun limpar() { if (!estado.value.ocupado) {
        professoresEditados.clear()
        token = null; site = null; _estado.value = MoodleState(disciplinas = estado.value.disciplinas)
    } }
    override fun onCleared() { token = null; site = null; super.onCleared() }
}
