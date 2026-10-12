package br.com.jogatina.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.jogatina.MainActivity
import br.com.jogatina.R

/**
 * Notificações do sistema (barra do Android). Postadas quando o app recebe
 * uma notificação em tempo real pelo WebSocket, mesmo em segundo plano
 * enquanto o processo estiver vivo.
 */
object SystemNotifier {

    private const val CHANNEL_ID = "jogatina_default"

    const val EXTRA_OPEN_NOTIFICATIONS = "br.com.jogatina.OPEN_NOTIFICATIONS"
    const val EXTRA_REFERENCE_TYPE = "br.com.jogatina.REFERENCE_TYPE"
    const val EXTRA_REFERENCE_ID = "br.com.jogatina.REFERENCE_ID"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun show(
        context: Context,
        id: String,
        title: String,
        body: String,
        referenceType: String?,
        referenceId: String?
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
            if (!referenceType.isNullOrBlank()) putExtra(EXTRA_REFERENCE_TYPE, referenceType)
            if (!referenceId.isNullOrBlank()) putExtra(EXTRA_REFERENCE_ID, referenceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS não concedida: ignora silenciosamente.
        }
    }
}
