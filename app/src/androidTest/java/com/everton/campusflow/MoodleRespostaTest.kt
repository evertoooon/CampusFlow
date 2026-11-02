package com.everton.campusflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.everton.campusflow.moodle.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoodleRespostaTest {
    private val api = MoodleUpf()
    @Test fun moduloRestritoPreservaTarefaDisponivelEAvisa() {
        val json = JSONObject("""{"courses":[{"id":7,"assignments":[{"id":12,"name":"Entrega &amp; revisão","duedate":1800000000}]}],"warnings":[{"item":"module","itemid":99,"warningcode":"1","message":"not exposed"}]}""")
        val r = api.decodificarTarefas(json,7)
        assertEquals(1,r.modulosRestritos)
        assertEquals("Entrega & revisão",r.tarefas.single().nome)
        assertEquals(1800000000000L,r.tarefas.single().prazo)
        assertEquals(12L,r.tarefas.single().id)
    }
    @Test fun cursoVazioValidoNaoViraBloqueio() {
        val r=api.decodificarTarefas(JSONObject("""{"courses":[{"id":7,"assignments":[]}],"warnings":[]}"""),7)
        assertTrue(r.tarefas.isEmpty()); assertEquals(0,r.modulosRestritos)
    }
    @Test fun todosModulosRestritosNaoSaoVazioSemAviso() {
        val r=api.decodificarTarefas(JSONObject("""{"courses":[{"id":7,"assignments":[]}],"warnings":[{"item":"module","warningcode":"1"}]}"""),7)
        assertTrue(r.tarefas.isEmpty()); assertEquals(1,r.modulosRestritos)
    }
    @Test fun bloqueioCursoOuCursoAusenteContinuaFalha() {
        val respostas=listOf(
            """{"courses":[],"warnings":[{"item":"course","itemid":7,"warningcode":"2"}]}""",
            """{"courses":[],"warnings":[]}""",
            """{"courses":[{"id":7,"assignments":[]}],"warnings":[{"item":"module","warningcode":"99"}]}""")
        respostas.forEach {
            try { api.decodificarTarefas(JSONObject(it),7); fail("Nao pode mascarar bloqueio como vazio") }
            catch(e:FalhaMoodle) { assertEquals(MotivoMoodle.ACESSO,e.motivo) }
        }
    }
    @Test fun envioDistingueRascunhoEquipeETentativaAtual() {
        assertEquals(EnvioMoodle.ENVIADO,api.decodificarEnvio(JSONObject("""{"lastattempt":{"submission":{"status":"submitted"}},"warnings":[]}"""),false))
        assertEquals(EnvioMoodle.RASCUNHO,api.decodificarEnvio(JSONObject("""{"lastattempt":{"submission":{"status":"draft"}}}"""),false))
        assertEquals(EnvioMoodle.PENDENTE,api.decodificarEnvio(JSONObject("""{"lastattempt":{"submission":{"status":"reopened"}},"previousattempts":[{"submission":{"status":"submitted"}}]}"""),false))
        val grupo=JSONObject("""{"lastattempt":{"submission":{"status":"draft"},"teamsubmission":{"status":"submitted"}}}""")
        assertEquals(EnvioMoodle.ENVIADO,api.decodificarEnvio(grupo,true))
        assertEquals(EnvioMoodle.RASCUNHO,api.decodificarEnvio(grupo,false))
    }
    @Test fun envioAusenteAvisoOuEstadoDesconhecidoNaoConcluem() {
        for (json in listOf("{}", """{"lastattempt":{"submission":{"status":"future-state"}}}""",
            """{"lastattempt":{"submission":{"status":"submitted"}},"warnings":[{"item":"module"}]}""")) {
            assertEquals(EnvioMoodle.DESCONHECIDO,api.decodificarEnvio(JSONObject(json),false))
        }
        assertEquals(EnvioMoodle.DESCONHECIDO,api.decodificarEnvio(JSONObject("""{"lastattempt":{"submission":{"status":"submitted"}}}"""),true))
    }
    @Test fun professoresReconhecePapeisSemConfundirAlunosOuGestores() {
        val r=api.decodificarProfessores(org.json.JSONArray("""[
          {"id":1,"fullname":"Ana &amp; Silva","roles":[{"shortname":"editingteacher"}]},
          {"id":2,"fullname":"Aluno","roles":[{"shortname":"student"}]},
          {"id":3,"fullname":"Bruno","roles":[{"shortname":"teacher"}]},
          {"id":4,"fullname":"Gestor","roles":[{"shortname":"manager"}]},
          {"id":5,"fullname":"Papel personalizado","roles":[{"shortname":"docente_custom"}]},
          {"id":6,"fullname":"Sem papel"},
          {"id":1,"fullname":"Ana &amp; Silva","roles":[{"shortname":"teacher"}]}
        ]"""))
        assertEquals(mapOf(1L to "Ana & Silva",3L to "Bruno"),r)
        assertTrue(api.decodificarProfessores(org.json.JSONArray("[]")).isEmpty())
    }
}
