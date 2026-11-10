package com.everton.campusflow.lembretes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import androidx.work.*
import com.everton.campusflow.data.*
import java.time.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

enum class EtapaAutomatica { VESPERA, ATRASO }

object PlanoAutomatico {
    fun diaAtraso(prazo: Long, agora: Instant, fuso: ZoneId): Long {
        val previsto = LocalDate.ofEpochDay(prazo).plusDays(1)
        val hoje = agora.atZone(fuso).toLocalDate()
        val proximo = if (hoje.atTime(9, 0).atZone(fuso).toInstant() > agora) hoje else hoje.plusDays(1)
        return maxOf(previsto, proximo).toEpochDay()
    }
    fun diaPrazo(atividade: Atividade, fuso: ZoneId): Long = atividade.prazoExatoEpochMillis?.let {
        Instant.ofEpochMilli(it).atZone(fuso).toLocalDate().toEpochDay()
    } ?: atividade.prazoEpochDay

    fun horario(atividade: Atividade, etapa: EtapaAutomatica, fuso: ZoneId): Long =
        LocalDate.ofEpochDay(if (etapa == EtapaAutomatica.VESPERA) diaPrazo(atividade, fuso) - 1
            else if (atividade.prazoExatoEpochMillis != null) maxOf(
                atividade.avisoAtrasoDia ?: Long.MIN_VALUE,
                com.everton.campusflow.prazos.Prazos.proximasNove(Instant.ofEpochMilli(atividade.prazoExatoEpochMillis), fuso))
            else atividade.avisoAtrasoDia ?: atividade.prazoEpochDay + 1)
            .atTime(9, 0).atZone(fuso).toInstant().toEpochMilli()

    fun pertinente(atividade: Atividade, etapa: EtapaAutomatica, agora: Instant, fuso: ZoneId): Boolean {
        val hoje = agora.atZone(fuso).toLocalDate().toEpochDay()
        return if (etapa == EtapaAutomatica.VESPERA) hoje == diaPrazo(atividade, fuso) - 1 &&
            horario(atividade, etapa, fuso) >= (atividade.lembretesAtivadosEm ?: Long.MIN_VALUE)
            else atividade.prazoExatoEpochMillis?.let { agora.toEpochMilli() >= it } ?: (hoje > atividade.prazoEpochDay)
    }
}

class EntregadorAutomatico(private val db: CampusFlowDatabase, private val publicador: PublicadorLembrete) {
    suspend fun entregar(id: Long, prazo: Long, etapa: EtapaAutomatica, horario: Long,
        agora: Instant, fuso: ZoneId): ResultadoLembrete = db.withTransaction {
        val dao = db.atividadeDao()
        val atividade = dao.buscarPorId(id) ?: return@withTransaction ResultadoLembrete.IGNORADO
        if (!atividade.lembretesAutomaticos || atividade.concluida || atividade.prazoEpochDay != prazo ||
            dao.etapaEntregue(id, prazo, etapa.name)) return@withTransaction ResultadoLembrete.IGNORADO
        if (PlanoAutomatico.horario(atividade, etapa, fuso) != horario)
            return@withTransaction ResultadoLembrete.IGNORADO
        if (agora.toEpochMilli() < horario) return@withTransaction ResultadoLembrete.ANTECIPADO
        if (!PlanoAutomatico.pertinente(atividade, etapa, agora, fuso))
            return@withTransaction ResultadoLembrete.IGNORADO
        if (!publicador.habilitado()) return@withTransaction ResultadoLembrete.BLOQUEADO
        publicador.publicarAutomatico(atividade, etapa)
        dao.registrarEntrega(EntregaAutomatica(id, prazo, etapa.name))
        ResultadoLembrete.ENVIADO
    }
}

class AutomaticoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val etapa = EtapaAutomatica.valueOf(inputData.getString("etapa") ?: "")
        val resultado = EntregadorAutomatico(CampusFlowDatabase.getInstance(applicationContext),
            Notificacoes(applicationContext)).entregar(inputData.getLong("id", 0),
            inputData.getLong("prazo", Long.MIN_VALUE), etapa, inputData.getLong("horario", 0),
            Instant.now(), ZoneId.systemDefault())
        // Uma alteração de fuso pode invalidar o token antigo: o próximo trabalho é reconciliado.
        if (resultado == ResultadoLembrete.IGNORADO) {
            WorkManager.getInstance(applicationContext).enqueue(OneTimeWorkRequestBuilder<ReconciliarLembretesWorker>().build())
        }
        if (resultado == ResultadoLembrete.ANTECIPADO) Result.retry() else Result.success()
    } catch (cancelamento: CancellationException) { throw cancelamento
    } catch (_: IllegalArgumentException) { Result.failure()
    } catch (_: SecurityException) { Result.success()
    } catch (_: Exception) { Result.retry() }
}

// Chamado sob o mutex de Lembretes, fora da thread da interface.
object Automaticos {
    private fun nome(id: Long, etapa: EtapaAutomatica) = "automatico-$id-${etapa.name}"
    suspend fun sincronizar(context: Context, atividade: Atividade?, id: Long, forcar: Boolean = false) {
        val work = WorkManager.getInstance(context)
        val dao = CampusFlowDatabase.getInstance(context).atividadeDao()
        val agora = Instant.now()
        val fuso = ZoneId.systemDefault()
        for (etapa in EtapaAutomatica.entries) {
            val nome = nome(id, etapa)
            if (atividade == null || !atividade.lembretesAutomaticos || atividade.concluida ||
                dao.etapaEntregue(id, atividade.prazoEpochDay, etapa.name)) {
                work.cancelUniqueWork(nome).result.get()
                continue
            }
            val horario = PlanoAutomatico.horario(atividade, etapa, fuso)
            // A véspera perdida não vira um aviso enganoso depois do vencimento.
            if (etapa == EtapaAutomatica.VESPERA && (horario < (atividade.lembretesAtivadosEm ?: Long.MIN_VALUE) ||
                agora.atZone(fuso).toLocalDate().toEpochDay() > PlanoAutomatico.diaPrazo(atividade, fuso) - 1)) {
                work.cancelUniqueWork(nome).result.get()
                continue
            }
            val token = "auto-${atividade.prazoEpochDay}-${etapa.name}-$horario"
            val existentes = work.getWorkInfosForUniqueWork(nome).get()
            if (!forcar && existentes.any { !it.state.isFinished && token in it.tags }) continue
            val request = OneTimeWorkRequestBuilder<AutomaticoWorker>()
                .setInputData(workDataOf("id" to id, "prazo" to atividade.prazoEpochDay,
                    "etapa" to etapa.name, "horario" to horario))
                .setInitialDelay((horario - agora.toEpochMilli()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag("campusflow-lembretes").addTag("atividade-$id").addTag(token).build()
            work.enqueueUniqueWork(nome, ExistingWorkPolicy.REPLACE, request).result.get()
        }
    }
}

class ReconciliarLembretesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        Lembretes.reconciliar(applicationContext, inputData.getBoolean("forcar", false))
        Result.success()
    } catch (cancelamento: CancellationException) { throw cancelamento
    } catch (_: Exception) { Result.retry() }
}

class RelogioLembretesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) return
        WorkManager.getInstance(context).enqueueUniqueWork("reconciliar-relogio", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReconciliarLembretesWorker>().setInputData(workDataOf("forcar" to true)).build())
    }
}
