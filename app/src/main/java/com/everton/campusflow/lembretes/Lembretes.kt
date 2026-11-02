package com.everton.campusflow.lembretes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.*
import com.everton.campusflow.*
import com.everton.campusflow.R
import com.everton.campusflow.data.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface PublicadorLembrete {
    fun habilitado(): Boolean
    fun publicar(atividade: Atividade)
    fun publicarAutomatico(atividade: Atividade, etapa: EtapaAutomatica) = publicar(atividade)
}

class Notificacoes(private val context: Context) : PublicadorLembrete {
    companion object { const val CANAL = "atividades_lembretes" }
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun prepararCanal() {
        manager.createNotificationChannel(NotificationChannel(CANAL,
            context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }
    override fun habilitado(): Boolean {
        prepararCanal()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CANAL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    @android.annotation.SuppressLint("MissingPermission") // Verificação runtime inclui permissão e canal.
    override fun publicar(atividade: Atividade) = publicarComTexto(atividade, R.string.reminder_notification_body)
    override fun publicarAutomatico(atividade: Atividade, etapa: EtapaAutomatica) = publicarComTexto(atividade,
        if (etapa == EtapaAutomatica.VESPERA) R.string.reminder_before_body else R.string.reminder_overdue_body)

    @android.annotation.SuppressLint("MissingPermission")
    private fun publicarComTexto(atividade: Atividade, texto: Int) {
        if (!habilitado()) throw SecurityException("Notificações desativadas")
        prepararCanal()
        val intent = Intent(context, AtividadeFormActivity::class.java)
            .setData(Uri.parse("campusflow://atividade/${atividade.id}"))
            .putExtra(AtividadeFormActivity.EXTRA_ATIVIDADE_ID, atividade.id)
        val stack = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .addNextIntent(Intent(context, AtividadesActivity::class.java))
            .addNextIntent(intent)
        val pendente = stack.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_lembrete).setContentTitle(atividade.titulo)
            .setContentText(context.getString(texto))
            .setContentIntent(pendente).setAutoCancel(true).build()
        // habilitado() é conferido pelo entregador; a permissão pode ser revogada entre chamadas.
        manager.notify("atividade-${atividade.id}", 0, notification)
    }
    fun cancelar(id: Long) { manager.cancel("atividade-$id", 0) }
}

enum class ResultadoLembrete { ENVIADO, IGNORADO, ANTECIPADO, BLOQUEADO }

class EntregadorLembrete(private val db: CampusFlowDatabase, private val publicador: PublicadorLembrete) {
    suspend fun entregar(id: Long, horario: Long, agora: Long): ResultadoLembrete = db.withTransaction {
        val atividade = db.atividadeDao().buscarPorId(id) ?: return@withTransaction ResultadoLembrete.IGNORADO
        if (atividade.concluida || atividade.lembreteEnviado || atividade.lembreteEpochMillis != horario)
            return@withTransaction ResultadoLembrete.IGNORADO
        if (horario > agora) return@withTransaction ResultadoLembrete.ANTECIPADO
        if (!publicador.habilitado()) return@withTransaction ResultadoLembrete.BLOQUEADO
        // Releitura, publicação e marcação compartilham a transação contra alterações concorrentes.
        publicador.publicar(atividade)
        check(db.atividadeDao().marcarLembreteEnviado(id, horario) == 1)
        ResultadoLembrete.ENVIADO
    }
}

class LembreteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("id", 0)
        val horario = inputData.getLong("horario", 0)
        if (id <= 0 || horario <= 0) return Result.failure()
        return try {
            val resultado = EntregadorLembrete(CampusFlowDatabase.getInstance(applicationContext),
                Notificacoes(applicationContext)).entregar(id, horario, System.currentTimeMillis())
            if (resultado == ResultadoLembrete.ANTECIPADO) Result.retry() else Result.success()
        } catch (cancelamento: CancellationException) { throw cancelamento
        } catch (_: SecurityException) { Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

object Lembretes {
    private val mutex = Mutex()
    private const val TAG = "campusflow-lembretes"
    private fun nome(id: Long) = "lembrete-atividade-$id"

    // Executado fora da thread da interface. KEEP conserva o trabalho se o horário não mudou.
    suspend fun sincronizar(context: Context, id: Long, forcarAutomaticos: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val work = WorkManager.getInstance(context)
            val atividade = CampusFlowDatabase.getInstance(context).atividadeDao().buscarPorId(id)
            Automaticos.sincronizar(context, atividade, id, forcarAutomaticos)
            val horario = atividade?.lembreteEpochMillis
            val notificacoes = Notificacoes(context)
            if (atividade == null || atividade.concluida || horario == null) {
                work.cancelUniqueWork(nome(id)).result.get()
                if (atividade == null || atividade.concluida || !atividade.lembretesAutomaticos) notificacoes.cancelar(id)
                return@withLock
            }
            if (atividade.lembreteEnviado) return@withLock
            val existentes = work.getWorkInfosForUniqueWork(nome(id)).get()
            val token = "horario-$horario"
            if (existentes.any { !it.state.isFinished && token in it.tags }) return@withLock
            // Não recria avisos vencidos ao reabrir atividades ou ao voltar ao aplicativo.
            if (horario <= System.currentTimeMillis()) return@withLock
            notificacoes.cancelar(id)
            val request = OneTimeWorkRequestBuilder<LembreteWorker>()
                .setInputData(workDataOf("id" to id, "horario" to horario))
                .setInitialDelay((horario - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(TAG).addTag(token).addTag("atividade-$id").build()
            work.enqueueUniqueWork(nome(id), ExistingWorkPolicy.REPLACE, request).result.get()
        }
    }

    suspend fun reconciliar(context: Context, forcarAutomaticos: Boolean = false) = withContext(Dispatchers.IO) {
        val ids = CampusFlowDatabase.getInstance(context).atividadeDao().buscarTodas().map { it.id }.toSet()
        ids.forEach { sincronizar(context, it, forcarAutomaticos) }
        val work = WorkManager.getInstance(context)
        work.getWorkInfosByTag(TAG).get().filter { !it.state.isFinished }.forEach { info ->
            val id = info.tags.firstOrNull { it.startsWith("atividade-") }?.removePrefix("atividade-")?.toLongOrNull()
            if (id != null && id !in ids) sincronizar(context, id)
        }
    }
}
