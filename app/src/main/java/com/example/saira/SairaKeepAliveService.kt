package com.example.saira

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.MainActivity
import com.example.R
import com.example.data.repository.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full edition only. A tiny foreground service that keeps Chatbox's process alive
 * (and online during Doze) so WhatsApp/Messenger auto-replies still go out after the
 * user clears recent apps and turns the screen off. It also re-binds the
 * notification listener periodically in case the OEM killed it.
 */
class SairaKeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val rebindTick = object : Runnable {
        override fun run() {
            if (SairaNotificationListenerService.isNotificationAccessGranted(this@SairaKeepAliveService)) {
                SairaNotificationListenerService.ensureBound(this@SairaKeepAliveService)
            }
            handler.postDelayed(this, REBIND_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        handler.postDelayed(rebindTick, REBIND_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Only (re)enter foreground when needed: first start, or to add the location
        // type once permission exists. Re-calling from background could drop it.
        if (!inForeground || (!hasLocationType && SairaLocationHelper.hasLocationPermission(this) &&
                intent?.getBooleanExtra(EXTRA_FROM_FOREGROUND, false) == true)
        ) {
            goForeground()
        }
        if (SairaNotificationListenerService.isNotificationAccessGranted(this)) {
            SairaNotificationListenerService.ensureBound(this)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Recents swipe: the service keeps running (stopWithTask=false); just make
        // sure the listener is still bound.
        super.onTaskRemoved(rootIntent)
        if (SairaNotificationListenerService.isNotificationAccessGranted(this)) {
            SairaNotificationListenerService.ensureBound(this)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(rebindTick)
        super.onDestroy()
    }

    private var inForeground = false
    private var hasLocationType = false

    private fun goForeground() {
        val notification = buildNotification(this)
        val wantLocation = SairaLocationHelper.hasLocationPermission(this)
        // Location type gives replies live location while the screen is off.
        // Only allowed when started from the foreground; else fall back.
        if (wantLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (runCatching { startForeground(NOTIFICATION_ID, notification, type) }.isSuccess) {
                inForeground = true
                hasLocationType = true
                return
            }
        }
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (ok) {
            inForeground = true
            hasLocationType = false
        } else if (!inForeground) {
            stopSelf()
        }
    }

    companion object {
        private const val CHANNEL_ID = "saira_auto_reply_keepalive"
        private const val NOTIFICATION_ID = 4107
        private const val REBIND_INTERVAL_MS = 10 * 60 * 1000L
        private const val EXTRA_FROM_FOREGROUND = "from_foreground"

        /** True only in the full edition, where the service is declared in the manifest. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.packageManager.getServiceInfo(
                ComponentName(context, SairaKeepAliveService::class.java),
                PackageManager.GET_META_DATA
            )
            true
        }.getOrDefault(false)

        fun start(context: Context, fromForeground: Boolean = false) {
            if (!isAvailable(context)) return
            val intent = Intent(context, SairaKeepAliveService::class.java)
                .putExtra(EXTRA_FROM_FOREGROUND, fromForeground)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            if (!isAvailable(context)) return
            runCatching { context.stopService(Intent(context, SairaKeepAliveService::class.java)) }
        }

        /** Starts or stops the service according to the saved toggle + permission. */
        fun sync(context: Context, fromForeground: Boolean = false) {
            val app = context.applicationContext
            if (!isAvailable(app)) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val enabled = runCatching {
                    AppPreferencesRepository(app).sairaNotifReplyEnabled.first()
                }.getOrDefault(false)
                val granted = SairaNotificationListenerService.isNotificationAccessGranted(app)
                if (enabled && granted) start(app, fromForeground) else stop(app)
            }
        }

        private fun buildNotification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null &&
                manager.getNotificationChannel(CHANNEL_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Saira Auto-Reply", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "Keeps Saira ready to auto-reply on WhatsApp/Messenger"
                        setShowBadge(false)
                    }
                )
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            return builder
                .setSmallIcon(R.drawable.ic_stat_saira)
                .setContentTitle("Saira Auto-Reply চালু")
                .setContentText("WhatsApp/Messenger-এ আপনার হয়ে উত্তর দিতে প্রস্তুত")
                .setOngoing(true)
                .setContentIntent(open)
                .build()
        }
    }
}

/** Restarts the keep-alive service after reboot / app update (full edition only). */
class SairaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val pending = goAsync()
                Handler(Looper.getMainLooper()).post {
                    SairaKeepAliveService.sync(context)
                    if (SairaNotificationListenerService.isNotificationAccessGranted(context)) {
                        SairaNotificationListenerService.ensureBound(context)
                    }
                    pending.finish()
                }
            }
        }
    }
}
