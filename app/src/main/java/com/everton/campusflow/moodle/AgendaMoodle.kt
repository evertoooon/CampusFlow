package com.everton.campusflow.moodle

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

object AgendaMoodle {
    internal const val NOME = "campusflow-moodle-atualizacao"
    internal const val GERACAO = "geracao"
    internal fun pedido(geracao: String): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<MoodleSyncWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(GERACAO to geracao))
            .addTag("moodle-geracao-$geracao").build()

    // Chamado sob sessaoMutex; token nunca entra em WorkManager Data/tags.
    internal fun reconciliar(context: Context, sessao: SessaoPersistida?, nome: String = NOME) {
        val work = WorkManager.getInstance(context)
        if (sessao == null || !sessao.segundoPlano || sessao.bloqueada) {
            work.cancelUniqueWork(nome).result.get(); return
        }
        val tag = "moodle-geracao-${sessao.geracao}"
        val mesmos = work.getWorkInfosForUniqueWork(nome).get().any { !it.state.isFinished && tag in it.tags }
        if (!mesmos) work.enqueueUniquePeriodicWork(nome, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            pedido(sessao.geracao)).result.get()
    }
}

class MoodleSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val geracao = inputData.getString(AgendaMoodle.GERACAO)?.takeIf { it.isNotBlank() } ?: return Result.success()
        return try {
            val resultado = ConexaoMoodle.atualizar(applicationContext, forcar = runAttemptCount > 0, geracaoEsperada = geracao)
            if ((resultado == ResultadoSync.FALHA_TRANSITORIA || resultado == ResultadoSync.OCUPADA) && runAttemptCount < 3)
                Result.retry() else Result.success()
        } catch (c: CancellationException) { throw c
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.success() }
    }
}
