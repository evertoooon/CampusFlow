package com.everton.campusflow.moodle

import android.content.Context
import com.everton.campusflow.data.CampusFlowDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ConexaoMoodleState(val conectada: Boolean=false, val atualizando:Boolean=false,
    val ultima:Long=0, val novas:Int=0, val reconectar:Boolean=false, val falhou:Boolean=false,
    val parcial:Boolean=false, val segundoPlano:Boolean=false, val avisarNovas:Boolean=false,
    val falhaAgenda:Boolean=false)

enum class ResultadoSync { ATUALIZADA, IGNORADA, OCUPADA, FALHA_TRANSITORIA, BLOQUEADA }

object ConexaoMoodle {
    internal val sessaoMutex=Mutex()
    private val consultaMutex=Mutex()
    private val _estado=MutableStateFlow(ConexaoMoodleState())
    val estado=_estado.asStateFlow()
    private fun estado(s:SessaoPersistida) = ConexaoMoodleState(conectada=true,ultima=s.ultima,
        reconectar=s.bloqueada,segundoPlano=s.segundoPlano,avisarNovas=s.avisarNovas)
    internal fun sinalizarFalhaConexao() { _estado.value=ConexaoMoodleState(falhou=true,reconectar=true) }

    suspend fun carregar(context:Context) = withContext(Dispatchers.IO) { sessaoMutex.withLock {
        try {
            val s=CofreMoodle(context).ler()
            val rodando=_estado.value.atualizando
            _estado.value=(s?.let(::estado)?:ConexaoMoodleState()).copy(atualizando=rodando)
            agendarBloqueado(context,s)
        } catch (_:Exception) { _estado.value=ConexaoMoodleState(reconectar=true); agendarBloqueado(context,null) }
    } }
    private fun agendarBloqueado(context:Context,s:SessaoPersistida?) {
        try { AgendaMoodle.reconciliar(context,s) }
        catch (_:Exception) { _estado.update { it.copy(falhaAgenda=true) } }
    }
    // Chamado sob sessaoMutex, após confirmação da importação. Uma nova conexão começa sem opt-in de segundo plano.
    internal fun conectarBloqueado(context:Context,token:String,usuario:Long,cursos:List<CursoMoodle>) {
        val s=SessaoPersistida(token,usuario,UUID.randomUUID().toString(),cursos)
        CofreMoodle(context).salvar(s); _estado.value=estado(s)
        AvisoMoodle(context).cancelar()
        agendarBloqueado(context,s)
    }
    suspend fun preferencias(context:Context,segundoPlano:Boolean?=null,avisarNovas:Boolean?=null) = withContext(Dispatchers.IO) {
        sessaoMutex.withLock {
            try {
                val cofre=CofreMoodle(context); val s=cofre.ler()?:return@withLock
                if(s.bloqueada) return@withLock
                val novo=s.com(segundoPlano=segundoPlano?:s.segundoPlano,avisarNovas=avisarNovas?:s.avisarNovas)
                cofre.salvar(novo)
                _estado.value=estado(novo).copy(atualizando=_estado.value.atualizando)
                if(!novo.avisarNovas) AvisoMoodle(context).cancelar()
                agendarBloqueado(context,novo)
            } catch (_:Exception) { _estado.update { it.copy(falhou=true) } }
        }
    }
    suspend fun desconectar(context:Context) = withContext(Dispatchers.IO) { sessaoMutex.withLock {
        try { CofreMoodle(context).apagar(); _estado.value=ConexaoMoodleState() }
        catch (_:Exception) { _estado.value=ConexaoMoodleState(falhou=true,reconectar=true) }
        agendarBloqueado(context,null); AvisoMoodle(context).cancelar()
    } }
    suspend fun atualizar(context:Context,forcar:Boolean=false,geracaoEsperada:String?=null):ResultadoSync =
        executar(context,forcar,MoodleUpf(),CofreMoodle(context),CampusFlowDatabase.getInstance(context),geracaoEsperada,AvisoMoodle(context)) { ids ->
            for(id in ids) com.everton.campusflow.lembretes.Lembretes.sincronizar(context,id)
        }

    internal suspend fun executar(context:Context,forcar:Boolean,api:AcessoMoodle,cofre:CofreMoodle,
        db:CampusFlowDatabase,geracaoEsperada:String?=null,aviso:AvisoNovidades=AvisoNovidades { },
        reconciliar: suspend (List<Long>) -> Unit = { }):ResultadoSync = withContext(Dispatchers.IO) {
        if(!consultaMutex.tryLock()) return@withContext ResultadoSync.OCUPADA
        var geracao:String?=null
        try {
            val agora=System.currentTimeMillis()
            val sessao=sessaoMutex.withLock {
                val s=try { cofre.ler() } catch (_:Exception) {
                    _estado.value=ConexaoMoodleState(reconectar=true); return@withContext ResultadoSync.BLOQUEADA
                } ?: return@withContext ResultadoSync.IGNORADA
                if(geracaoEsperada!=null && (s.geracao!=geracaoEsperada || !s.segundoPlano)) return@withContext ResultadoSync.IGNORADA
                if(s.bloqueada) { _estado.value=estado(s); return@withContext ResultadoSync.BLOQUEADA }
                if(!forcar && s.tentativa>0 && agora-s.tentativa in 0 until 900_000) return@withContext ResultadoSync.IGNORADA
                geracao=s.geracao
                cofre.salvar(s.com(tentativa=agora))
                _estado.value=estado(s).copy(atualizando=true); s
            }
            val site=runInterruptible { api.site(sessao.token) }
            if(site.usuarioId!=sessao.usuario) throw FalhaMoodle(MotivoMoodle.ACESSO,true)
            if("mod_assign_get_assignments" !in site.funcoes) throw FalhaMoodle(MotivoMoodle.ACESSO)
            var total=0; var estados=0; var statusDisponivel="mod_assign_get_submission_status" in site.funcoes
            var parcial=false
            val previa=runInterruptible {
                sessao.cursos.map { curso ->
                    val r=api.consultarTarefas(sessao.token,curso.id)
                    total+=r.tarefas.size; require(total<=500)
                    if(r.modulosRestritos>0) parcial=true
                    val tarefas=r.tarefas.map { tarefa ->
                        if(!statusDisponivel || estados>=100) { parcial=true; tarefa } else {
                            estados++
                            val envio=try { api.envio(sessao.token,tarefa) }
                            catch(c:CancellationException) { throw c }
                            catch(f:FalhaMoodle) { if(f.sessaoInvalida) throw f; statusDisponivel=false; EnvioMoodle.DESCONHECIDO }
                            catch(_:Exception) { statusDisponivel=false; EnvioMoodle.DESCONHECIDO }
                            if(envio==EnvioMoodle.DESCONHECIDO) parcial=true
                            tarefa.copy(envio=envio)
                        }
                    }
                    PreviaCursoMoodle(curso,tarefas)
                }
            }
            var alteradas: List<Long> = emptyList()
            val fim = sessaoMutex.withLock {
                val atual=cofre.ler()
                if(atual?.geracao!=sessao.geracao || atual.bloqueada ||
                    (geracaoEsperada!=null && !atual.segundoPlano)) return@withContext ResultadoSync.IGNORADA
                currentCoroutineContext().ensureActive()
                val origem="${MoodleUpf.ORIGEM}/usuario/${sessao.usuario}"
                val validas=previa.mapNotNull { p ->
                    db.cursoImportadoDao().buscar(origem,p.curso.id)?.let { p.copy(disciplinaId=it) }
                }
                val resultado=if(validas.isEmpty()) null else ImportadorMoodle(db).importar(sessao.usuario,validas,conciliarAutomaticamente=true)
                alteradas=resultado?.alteradas.orEmpty()
                val novas=resultado?.atividadesCriadas?:0
                cofre.salvar(atual.com(ultima=System.currentTimeMillis()))
                _estado.value=estado(atual).copy(ultima=System.currentTimeMillis(),novas=novas,
                    parcial=parcial || validas.size!=previa.size)
                // A preferência e a sessão são relidas sob o lock; a permissão Android é conferida pelo publicador.
                if(atual.avisarNovas && novas>0) try { aviso.publicar(novas) } catch (_:Exception) { /* dados já salvos */ }
                ResultadoSync.ATUALIZADA
            }
            try { reconciliar(alteradas) }
            catch(c:CancellationException) { throw c } catch(_:Exception) { /* retomada também reconcilia */ }
            fim
        } catch(c:CancellationException) { throw c
        } catch(e:Exception) {
            val invalida=(e as? FalhaMoodle)?.sessaoInvalida==true
            sessaoMutex.withLock {
                val atual=try { cofre.ler() } catch (_:Exception) {
                    _estado.value=ConexaoMoodleState(reconectar=true); null
                }
                if(atual!=null && atual.geracao==geracao) {
                    if(invalida) try { cofre.salvar(atual.com(bloqueada=true)) } catch (_:Exception) { /* informar reconexão mesmo se o disco falhar */ }
                    _estado.value=estado(atual).copy(reconectar=invalida,falhou=true)
                }
            }
            if(invalida || (e as? FalhaMoodle)?.motivo==MotivoMoodle.ACESSO) ResultadoSync.BLOQUEADA
            else ResultadoSync.FALHA_TRANSITORIA
        } finally {
            withContext(NonCancellable) { sessaoMutex.withLock {
                if(_estado.value.atualizando) _estado.update { it.copy(atualizando=false) }
            } }
            consultaMutex.unlock()
        }
    }
}
