package com.everton.campusflow.moodle

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.everton.campusflow.AtividadesActivity
import com.everton.campusflow.MainActivity
import com.everton.campusflow.R

internal fun interface AvisoNovidades { fun publicar(quantidade: Int) }

internal class AvisoMoodle(private val context: Context) : AvisoNovidades {
    companion object { const val CANAL = "moodle_novidades" }
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun permitido(): Boolean {
        manager.createNotificationChannel(NotificationChannel(CANAL,context.getString(R.string.moodle_news_channel),NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CANAL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    @android.annotation.SuppressLint("MissingPermission") // Rechecagem runtime de permissão, app e canal.
    override fun publicar(quantidade: Int) {
        if(quantidade<=0 || !permitido()) return
        val pendente=TaskStackBuilder.create(context)
            .addNextIntent(Intent(context,MainActivity::class.java))
            .addNextIntent(Intent(context,AtividadesActivity::class.java))
            .getPendingIntent(2026,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,CANAL).setSmallIcon(R.drawable.ic_lembrete)
            .setContentTitle(context.getString(R.string.moodle_news_title))
            .setContentText(context.getString(R.string.moodle_news_body,quantidade))
            .setContentIntent(pendente).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        manager.notify("moodle-novidades",0,n)
    }
    fun cancelar() { manager.cancel("moodle-novidades",0) }
}
