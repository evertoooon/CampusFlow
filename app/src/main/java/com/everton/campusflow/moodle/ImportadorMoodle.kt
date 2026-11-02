package com.everton.campusflow.moodle

import androidx.room.withTransaction
import com.everton.campusflow.data.*
import java.time.*

data class PreviaCursoMoodle(val curso: CursoMoodle, val tarefas: List<TarefaMoodle>,
    val disciplinaId: Long? = null, val modulosRestritos: Int = 0,
    val professores: ProfessoresMoodle = ProfessoresMoodle(), val professor: String = "")
data class ImportacaoMoodle(val disciplinasCriadas: Int, val atividadesCriadas: Int, val repetidas: Int,
    val conclusoesAtualizadas: Int = 0, val alteradas: List<Long> = emptyList())

class ImportadorMoodle(private val db: CampusFlowDatabase) {
    suspend fun importar(usuarioId: Long, previa: List<PreviaCursoMoodle>, atualizarConclusoes: Boolean = false,
        conciliarAutomaticamente: Boolean = false): ImportacaoMoodle = db.withTransaction {
        require(usuarioId > 0 && previa.size in 1..20)
        require(previa.map { it.curso.id }.distinct().size == previa.size)
        val origem = "${MoodleUpf.ORIGEM}/usuario/$usuarioId"
        previa.forEach { p ->
            require(p.curso.id > 0 && p.curso.nome.isNotBlank() && p.tarefas.size <= 500)
            require(p.professor.length <= 500)
            require(p.tarefas.all { it.id > 0 && it.nome.isNotBlank() })
            require(p.tarefas.map { it.id }.distinct().size == p.tarefas.size)
            p.disciplinaId?.let { require(db.disciplinaDao().buscarPorId(it) != null) }
        }
        var disciplinas = 0; var atividades = 0; var repetidas = 0; var concluidas = 0
        val alteradas = mutableListOf<Long>()
        for (p in previa) {
            val disciplina = db.cursoImportadoDao().buscar(origem, p.curso.id) ?: run {
                val id = p.disciplinaId ?: db.disciplinaDao().inserir(Disciplina(nome = p.curso.nome,
                    professor = p.professor.trim())).also { disciplinas++ }
                db.cursoImportadoDao().inserir(CursoImportado(origem, p.curso.id, id)); id
            }
            for (tarefa in p.tarefas) {
                val uid = "assign/${tarefa.id}"
                val existente = db.atividadeDao().buscarImportado(origem, uid)
                if (existente != null) {
                    repetidas++
                    if (atualizarConclusoes && tarefa.envio == EnvioMoodle.ENVIADO &&
                        db.atividadeDao().buscarPorId(existente)?.concluida == false) {
                        db.atividadeDao().atualizarConcluida(existente, true); concluidas++
                        alteradas += existente
                    }
                    val local = db.atividadeDao().buscarPorId(existente)!!
                    val conciliada = ConciliacaoMoodle.conciliar(local,db.controleMoodleDao().buscar(existente),tarefa,conciliarAutomaticamente)
                    if (conciliada.atividade != local) {
                        db.atividadeDao().atualizar(conciliada.atividade); alteradas += existente
                    }
                    db.controleMoodleDao().salvar(conciliada.controle)
                    continue
                }
                val prazo = tarefa.prazo ?: continue
                val dia = Instant.ofEpochMilli(prazo).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                val id = db.atividadeDao().inserir(Atividade(titulo = tarefa.nome, disciplinaId = disciplina,
                    tipo = TipoAtividade.TRABALHO, prioridade = PrioridadeAtividade.MEDIA,
                    prazoEpochDay = dia, prazoExatoEpochMillis = prazo, concluida = tarefa.envio == EnvioMoodle.ENVIADO))
                db.atividadeDao().registrarImportado(EventoImportado(origem, uid, id, "UTC")); atividades++
                db.controleMoodleDao().salvar(ConciliacaoMoodle.inicial(id,tarefa))
            }
        }
        ImportacaoMoodle(disciplinas, atividades, repetidas, concluidas, alteradas.distinct())
    }
}
