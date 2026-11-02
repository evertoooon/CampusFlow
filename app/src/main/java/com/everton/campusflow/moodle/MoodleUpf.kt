package com.everton.campusflow.moodle

import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class MoodleUpf : AcessoMoodle {
    companion object { const val ORIGEM = "https://ead.upf.br" }
    // Host e caminhos fixos. Credenciais não entram na URL, em redirects ou em logs.
    private fun post(caminho: String, campos: Map<String, String>): Any {
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
        val conexao = URL(ORIGEM + caminho).openConnection() as HttpsURLConnection
        try {
            conexao.requestMethod = "POST"
            conexao.instanceFollowRedirects = false
            conexao.connectTimeout = 15_000
            conexao.readTimeout = 20_000
            conexao.useCaches = false
            conexao.doOutput = true
            conexao.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conexao.setRequestProperty("Accept", "application/json")
            val corpo = campos.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
                .toByteArray(Charsets.UTF_8)
            try {
                conexao.outputStream.use { it.write(corpo) }
            } finally { corpo.fill(0) }
            if (conexao.responseCode != 200) throw FalhaMoodle(MotivoMoodle.REDE)
            val bytes = conexao.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val lidos = input.read(buffer)
                    if (lidos < 0) break
                    total += lidos
                    if (total > 1_048_576) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
                    output.write(buffer, 0, lidos)
                }
                output.toByteArray()
            }
            val texto = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val json = JSONTokener(texto).nextValue()
            if (json is JSONObject && json.has("errorcode")) {
                throw FalhaMoodle(if (json.optString("errorcode") == "invalidlogin") MotivoMoodle.CREDENCIAIS else MotivoMoodle.ACESSO,
                    json.optString("errorcode") == "invalidtoken")
            }
            if (json !is JSONObject && json !is JSONArray) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
            return json
        } catch (falha: FalhaMoodle) { throw falha
        } catch (_: java.io.IOException) { throw FalhaMoodle(MotivoMoodle.REDE)
        } catch (_: Exception) { throw FalhaMoodle(MotivoMoodle.RESPOSTA)
        } finally { conexao.disconnect() }
    }
    private fun chamada(token: String, funcao: String, argumentos: Map<String, String> = emptyMap()): Any =
        post("/webservice/rest/server.php", argumentos + mapOf("wstoken" to token,
            "wsfunction" to funcao, "moodlewsrestformat" to "json"))
    private fun objeto(valor: Any) = valor as? JSONObject ?: throw FalhaMoodle(MotivoMoodle.RESPOSTA)
    private fun lista(valor: Any) = valor as? JSONArray ?: throw FalhaMoodle(MotivoMoodle.RESPOSTA)
    private fun nome(objeto: JSONObject, chave: String): String = androidx.core.text.HtmlCompat.fromHtml(
        objeto.getString(chave), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        .take(500).ifBlank { throw FalhaMoodle(MotivoMoodle.RESPOSTA) }
    override fun autenticar(usuario: String, senha: String): String {
        val json = objeto(post("/login/token.php", mapOf("username" to usuario, "password" to senha,
            "service" to "moodle_mobile_app")))
        return json.optString("token").takeIf { it.matches(Regex("[a-zA-Z0-9]{16,256}")) }
            ?: throw FalhaMoodle(MotivoMoodle.RESPOSTA)
    }
    override fun site(token: String): SiteMoodle {
        val json = objeto(chamada(token, "core_webservice_get_site_info"))
        val usuario = json.getLong("userid")
        if (usuario <= 0) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
        val funcoes = json.getJSONArray("functions")
        if (funcoes.length() > 2_000) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
        return SiteMoodle(usuario, (0 until funcoes.length()).map { funcoes.getJSONObject(it).getString("name") }.toSet())
    }
    override fun cursos(token: String, usuarioId: Long): List<CursoMoodle> {
        val json = lista(chamada(token, "core_enrol_get_users_courses", mapOf("userid" to usuarioId.toString())))
        if (json.length() > 200) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
        return (0 until json.length()).map {
            val curso = json.getJSONObject(it)
            val id = curso.getLong("id")
            if (id <= 0) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
            CursoMoodle(id, nome(curso, "fullname"), curso.optLong("startdate", 0))
        }
    }
    override fun tarefas(token: String, cursoId: Long): List<TarefaMoodle> {
        return consultarTarefas(token, cursoId).tarefas
    }
    override fun consultarTarefas(token: String, cursoId: Long): TarefasMoodle {
        val json = objeto(chamada(token, "mod_assign_get_assignments", mapOf("courseids[0]" to cursoId.toString())))
        return decodificarTarefas(json, cursoId)
    }
    internal fun decodificarTarefas(json: JSONObject, cursoId: Long): TarefasMoodle {
        val avisos = json.optJSONArray("warnings") ?: JSONArray()
        var restritos = 0
        for (i in 0 until avisos.length()) {
            val aviso = avisos.getJSONObject(i)
            // O Moodle pode omitir um módulo restrito e ainda retornar o restante do curso.
            if (aviso.optString("item") == "module" && aviso.optString("warningcode") == "1") restritos++
            else throw FalhaMoodle(MotivoMoodle.ACESSO)
        }
        val cursos = json.getJSONArray("courses")
        var encontrado = false
        val resultado = mutableListOf<TarefaMoodle>()
        for (i in 0 until cursos.length()) {
            val curso = cursos.getJSONObject(i)
            if (curso.getLong("id") != cursoId) continue
            encontrado = true
            val tarefas = curso.getJSONArray("assignments")
            if (tarefas.length() > 500) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
            for (j in 0 until tarefas.length()) {
                val tarefa = tarefas.getJSONObject(j)
                val segundos = tarefa.optLong("duedate", 0)
                val prazo = if (segundos > 0) Math.multiplyExact(segundos, 1_000L) else null
                val id = tarefa.getLong("id")
                if (id <= 0) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
                resultado += TarefaMoodle(nome(tarefa, "name"), prazo, id,
                    emGrupo = tarefa.optInt("teamsubmission", 0) == 1)
            }
        }
        if (!encontrado) throw FalhaMoodle(MotivoMoodle.ACESSO)
        return TarefasMoodle(resultado, restritos)
    }
    override fun envio(token: String, tarefa: TarefaMoodle): EnvioMoodle = decodificarEnvio(
        objeto(chamada(token, "mod_assign_get_submission_status", mapOf("assignid" to tarefa.id.toString()))), tarefa.emGrupo)

    internal fun decodificarEnvio(json: JSONObject, emGrupo: Boolean): EnvioMoodle {
        if ((json.optJSONArray("warnings")?.length() ?: 0) > 0) return EnvioMoodle.DESCONHECIDO
        val tentativa = json.optJSONObject("lastattempt") ?: return EnvioMoodle.DESCONHECIDO
        val envio = tentativa.optJSONObject(if (emGrupo) "teamsubmission" else "submission")
            ?: return EnvioMoodle.DESCONHECIDO
        return when (envio.optString("status")) {
            "submitted" -> EnvioMoodle.ENVIADO
            "draft" -> EnvioMoodle.RASCUNHO
            "new", "reopened" -> EnvioMoodle.PENDENTE
            else -> EnvioMoodle.DESCONHECIDO
        }
    }
    override fun professores(token: String, cursoId: Long): ProfessoresMoodle {
        val docentes = linkedMapOf<Long, String>()
        // Somente campos mínimos; sem filtro por capability que exigiria role:review.
        for (pagina in 0 until 5) {
            val json = lista(chamada(token, "core_enrol_get_enrolled_users", mapOf(
                "courseid" to cursoId.toString(),
                "options[0][name]" to "userfields", "options[0][value]" to "id,fullname,roles",
                "options[1][name]" to "limitfrom", "options[1][value]" to (pagina * 50).toString(),
                "options[2][name]" to "limitnumber", "options[2][value]" to "50",
                "options[3][name]" to "sortby", "options[3][value]" to "id")))
            if (json.length() > 50) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
            docentes.putAll(decodificarProfessores(json))
            if (json.length() < 50) return ProfessoresMoodle(docentes.values.distinct(),
                if (docentes.isEmpty()) EstadoProfessores.SEM_IDENTIFICACAO else EstadoProfessores.ENCONTRADOS)
        }
        return ProfessoresMoodle(docentes.values.distinct(), EstadoProfessores.PARCIAL)
    }
    internal fun decodificarProfessores(json: JSONArray): Map<Long, String> {
        val docentes = linkedMapOf<Long, String>()
        for (i in 0 until json.length()) {
            val usuario = json.getJSONObject(i)
            val papeis = usuario.optJSONArray("roles") ?: continue
            val docente = (0 until papeis.length()).any {
                papeis.getJSONObject(it).optString("shortname") in setOf("teacher", "editingteacher")
            }
            if (docente) {
                val id = usuario.getLong("id")
                if (id <= 0) throw FalhaMoodle(MotivoMoodle.RESPOSTA)
                docentes[id] = nome(usuario, "fullname")
            }
        }
        return docentes
    }
}
