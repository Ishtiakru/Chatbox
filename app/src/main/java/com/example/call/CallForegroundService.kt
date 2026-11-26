package com.example.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Keeps the microphone (and camera for video) alive while a call is running,
 * even when the screen turns off or the user switches apps — Android blocks
 * background mic/camera access for apps without a foreground service.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peer = intent?.getStringExtra(EXTRA_PEER).orEmpty().ifBlank { "Chatbox" }
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val notification = buildNotification(peer, video)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var type = 0
                if (granted(Manifest.permission.RECORD_AUDIO)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (video && granted(Manifest.permission.CAMERA)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (type == 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (!started) stopSelf()
        return START_NOT_STICKY
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(peer: String, video: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this,
            7301,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_saira)
            .setContentTitle(if (video) "ভিডিও কল চলছে" else "অডিও কল চলছে")
            .setContentText("$peer — ফিরে যেতে ট্যাপ করুন")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "chatbox_ongoing_call_v1"
        private const val NOTIFICATION_ID = 7301
        private const val EXTRA_PEER = "peer"
        private const val EXTRA_VIDEO = "video"

        fun start(context: Context, peerName: String, isVideo: Boolean) {
            // Only start when the mic is granted: a started-but-never-foregrounded
            // service makes Android kill the app.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_PEER, peerName)
                .putExtra(EXTRA_VIDEO, isVideo)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallForegroundService::class.java)) }
        }
    }
}
