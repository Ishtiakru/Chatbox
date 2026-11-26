package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.example.MainActivity
import com.example.R

/**
 * Heads-up notifications WITH SOUND for new Chatbox messages and incoming
 * calls. IMPORTANCE_HIGH makes them pop over the home screen; the channel
 * sound/vibration make them audible while the screen is off.
 */
object ChatboxNotifier {
    private const val MSG_CHANNEL = "chatbox_messages_v1"
    private const val CALL_CHANNEL = "chatbox_calls_v1"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(MSG_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(MSG_CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "New Chatbox messages"
                        setSound(
                            Uri.DEFAULT,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 250, 250, 250)
                    }
            )
        }
        if (nm.getNotificationChannel(CALL_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CALL_CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Incoming Chatbox calls"
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                    }
            )
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** One notification per thread; re-notifies update the same entry. */
    fun notifyMessage(context: Context, threadId: String, title: String, body: String) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val id = ("msg::$threadId").hashCode()
        val notification = Notification.Builder(context, MSG_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_saira)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        nm.notify(id, notification)
    }

    fun notifyIncomingCall(context: Context, callKey: String, title: String, body: String) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val id = ("call::$callKey").hashCode()
        val notification = Notification.Builder(context, CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_saira)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppIntent(context))
            .build()
        nm.notify(id, notification)
    }

    fun cancelIncomingCall(context: Context, callKey: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(("call::$callKey").hashCode())
    }
}
