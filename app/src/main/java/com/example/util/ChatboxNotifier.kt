package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import com.example.MainActivity
import com.example.R

/**
 * Heads-up notifications WITH SOUND for new Chatbox messages and incoming
 * calls. IMPORTANCE_HIGH makes them pop over the home screen; the channel
 * sound/vibration make them audible while the screen is off.
 */
object ChatboxNotifier {
    /** An unanswered call stops ringing after this long (both sides log "Missed"). */
    const val RING_TIMEOUT_MS = 60_000L

    private fun msgChannel(context: Context) = ChatboxSounds.channelId(context, ChatboxSounds.Kind.MESSAGE)
    private fun callChannel(context: Context) = ChatboxSounds.channelId(context, ChatboxSounds.Kind.CALL)

    /** Creates the channels with the user's chosen tones (Profile → Notifications). */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val msgId = msgChannel(context)
        if (nm.getNotificationChannel(msgId) == null) {
            val tone = ChatboxSounds.tone(context, ChatboxSounds.Kind.MESSAGE)
            val vibrate = ChatboxSounds.vibrate(context, ChatboxSounds.Kind.MESSAGE)
            nm.createNotificationChannel(
                NotificationChannel(msgId, "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "New Chatbox messages"
                        setSound(
                            tone,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        enableVibration(vibrate)
                        vibrationPattern = if (vibrate) longArrayOf(0, 250, 250, 250) else null
                    }
            )
        }
        val callId = callChannel(context)
        if (nm.getNotificationChannel(callId) == null) {
            val tone = ChatboxSounds.tone(context, ChatboxSounds.Kind.CALL)
            val vibrate = ChatboxSounds.vibrate(context, ChatboxSounds.Kind.CALL)
            nm.createNotificationChannel(
                NotificationChannel(callId, "Calls", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Incoming Chatbox calls"
                        setSound(
                            tone,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        enableVibration(vibrate)
                        vibrationPattern = if (vibrate) longArrayOf(0, 1000, 500, 1000, 500, 1000) else null
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
        val notification = Notification.Builder(context, msgChannel(context))
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
        val notification = Notification.Builder(context, callChannel(context))
            .setSmallIcon(R.drawable.ic_stat_saira)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_CALL)
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .setContentIntent(openAppIntent(context))
            .build()
        // INSISTENT = ringtone + vibration repeat until the notification is cancelled
        // (accept / decline / caller hangs up / 60 s timeout) — not just one ring.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify(id, notification)
    }

    fun cancelIncomingCall(context: Context, callKey: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(("call::$callKey").hashCode())
    }
}
