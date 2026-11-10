package com.everton.campusflow.moodle

enum class MotivoMoodle { CREDENCIAIS, ACESSO, REDE, RESPOSTA }
class FalhaMoodle(val motivo: MotivoMoodle, val sessaoInvalida: Boolean = false) : Exception()
data class SiteMoodle(val usuarioId: Long, val funcoes: Set<String>)
data class CursoMoodle(val id: Long, val nome: String, val inicio: Long = 0)
enum class EnvioMoodle { ENVIADO, RASCUNHO, PENDENTE, DESCONHECIDO }
data class TarefaMoodle(val nome: String, val prazo: Long?, val id: Long = 0,
    val envio: EnvioMoodle = EnvioMoodle.DESCONHECIDO, val emGrupo: Boolean = false)
data class TarefasMoodle(val tarefas: List<TarefaMoodle>, val modulosRestritos: Int = 0)
enum class EstadoProfessores { ENCONTRADOS, SEM_IDENTIFICACAO, PARCIAL, NAO_LIBERADO, FALHOU }
data class ProfessoresMoodle(val nomes: List<String> = emptyList(),
    val estado: EstadoProfessores = EstadoProfessores.NAO_LIBERADO)

interface AcessoMoodle {
    fun autenticar(usuario: String, senha: String): String
    fun site(token: String): SiteMoodle
    fun cursos(token: String, usuarioId: Long): List<CursoMoodle>
    fun tarefas(token: String, cursoId: Long): List<TarefaMoodle>
    fun consultarTarefas(token: String, cursoId: Long): TarefasMoodle = TarefasMoodle(tarefas(token, cursoId))
    fun envio(token: String, tarefa: TarefaMoodle): EnvioMoodle = EnvioMoodle.DESCONHECIDO
    fun professores(token: String, cursoId: Long): ProfessoresMoodle = ProfessoresMoodle()
}
sealed interface ConsultaMoodle<out T> {
    data class Lida<T>(val dados: T) : ConsultaMoodle<T>
    data object NaoLiberada : ConsultaMoodle<Nothing>
    data object NaoTestada : ConsultaMoodle<Nothing>
    data class Falhou(val motivo: MotivoMoodle) : ConsultaMoodle<Nothing>
}
data class ResultadoMoodle(val cursos: ConsultaMoodle<List<CursoMoodle>>, val cursoAmostra: CursoMoodle?,
    val tarefas: ConsultaMoodle<List<TarefaMoodle>>)

// Não possui banco, arquivos, estado de sessão ou propriedades para guardar credenciais.
class VerificarMoodle(private val acesso: AcessoMoodle,
    private val sessao: (String, SiteMoodle) -> Unit = { _, _ -> }) {
    fun verificar(usuario: String, senha: String): ResultadoMoodle {
        require(usuario.isNotBlank() && senha.isNotEmpty())
        val token = acesso.autenticar(usuario.trim(), senha)
        val site = acesso.site(token)
        sessao(token, site)
        if ("core_enrol_get_users_courses" !in site.funcoes)
            return ResultadoMoodle(ConsultaMoodle.NaoLiberada, null, ConsultaMoodle.NaoTestada)
        val cursos = try { acesso.cursos(token, site.usuarioId).sortedByDescending { it.inicio }
        } catch (falha: FalhaMoodle) {
            return ResultadoMoodle(ConsultaMoodle.Falhou(falha.motivo), null, ConsultaMoodle.NaoTestada)
        }
        val amostra = cursos.firstOrNull()
            ?: return ResultadoMoodle(ConsultaMoodle.Lida(cursos), null, ConsultaMoodle.NaoTestada)
        if ("mod_assign_get_assignments" !in site.funcoes)
            return ResultadoMoodle(ConsultaMoodle.Lida(cursos), amostra, ConsultaMoodle.NaoLiberada)
        val tarefas = try { ConsultaMoodle.Lida(acesso.tarefas(token, amostra.id))
        } catch (falha: FalhaMoodle) { ConsultaMoodle.Falhou(falha.motivo) }
        return ResultadoMoodle(ConsultaMoodle.Lida(cursos), amostra, tarefas)
    }
}
