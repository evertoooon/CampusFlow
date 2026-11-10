package com.everton.campusflow

import com.everton.campusflow.moodle.*
import org.junit.Assert.*
import org.junit.Test

class VerificarMoodleTest {
    private class Simulado : AcessoMoodle {
        val chamadas = mutableListOf<String>()
        var funcoes = setOf("core_enrol_get_users_courses", "mod_assign_get_assignments")
        var cursos = listOf(CursoMoodle(2, "Anterior", 1), CursoMoodle(3, "Atual", 2))
        var erroCursos: MotivoMoodle? = null
        var erroLogin: MotivoMoodle? = null
        var erroTarefas: MotivoMoodle? = null
        override fun autenticar(usuario: String, senha: String): String {
            chamadas += "login"
            erroLogin?.let { throw FalhaMoodle(it) }
            return "token-ficticio"
        }
        override fun site(token: String): SiteMoodle { chamadas += "site"; return SiteMoodle(7, funcoes) }
        override fun cursos(token: String, usuarioId: Long): List<CursoMoodle> {
            chamadas += "cursos"
            erroCursos?.let { throw FalhaMoodle(it) }
            assertEquals(7L, usuarioId)
            return cursos
        }
        override fun tarefas(token: String, cursoId: Long): List<TarefaMoodle> {
            chamadas += "tarefas"
            erroTarefas?.let { throw FalhaMoodle(it) }
            assertEquals(3L, cursoId)
            return listOf(TarefaMoodle("Trabalho", null))
        }
    }
    private fun testar(s: Simulado) = VerificarMoodle(s).verificar("usuario-ficticio", "senha-ficticia")
    @Test fun consultaAmostraRecenteEConservaTarefaSemPrazo() {
        val s = Simulado(); val r = testar(s)
        assertEquals(listOf("login", "site", "cursos", "tarefas"), s.chamadas)
        assertEquals(3L, r.cursoAmostra!!.id)
        assertNull((r.tarefas as ConsultaMoodle.Lida).dados.single().prazo)
    }
    @Test fun funcaoAusenteNaoTentaAcessarDadosBloqueados() {
        val s = Simulado().apply { funcoes = emptySet() }
        assertEquals(ConsultaMoodle.NaoLiberada, testar(s).cursos)
        assertEquals(listOf("login", "site"), s.chamadas)
    }
    @Test fun cursoVazioELeituraValidaSemAmostra() {
        val s = Simulado().apply { cursos = emptyList() }
        val r = testar(s)
        assertEquals(emptyList<CursoMoodle>(), (r.cursos as ConsultaMoodle.Lida).dados)
        assertEquals(ConsultaMoodle.NaoTestada, r.tarefas)
    }
    @Test fun erroDeTarefaNaoApagaDisciplinasLidas() {
        val r = testar(Simulado().apply { erroTarefas = MotivoMoodle.ACESSO })
        assertEquals(2, (r.cursos as ConsultaMoodle.Lida).dados.size)
        assertEquals(ConsultaMoodle.Falhou(MotivoMoodle.ACESSO), r.tarefas)
    }
    @Test fun falhaDeCursosNaoEVazioConfirmado() {
        val r = testar(Simulado().apply { erroCursos = MotivoMoodle.REDE })
        assertEquals(ConsultaMoodle.Falhou(MotivoMoodle.REDE), r.cursos)
        assertEquals(ConsultaMoodle.NaoTestada, r.tarefas)
    }
    @Test fun credencialRecusadaInterrompeAntesDeConsultarDados() {
        val s = Simulado().apply { erroLogin = MotivoMoodle.CREDENCIAIS }
        try { testar(s); fail("Deve recusar o login simulado")
        } catch (f: FalhaMoodle) { assertEquals(MotivoMoodle.CREDENCIAIS, f.motivo); assertNull(f.message) }
        assertEquals(listOf("login"), s.chamadas)
    }
}
