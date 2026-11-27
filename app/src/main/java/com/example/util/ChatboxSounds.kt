package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The user's message tone, call ringtone and vibration choices.
 *
 * Android freezes a notification channel's sound once it is created, so a change
 * deletes the old channel and creates a new one with a bumped id
 * ("chatbox_messages_v2", "_v3" …). Version 1 = the original channels.
 */
object ChatboxSounds {
    private const val PREFS = "chatbox_sounds"
    private const val SILENT = "silent"

    enum class Kind(val key: String, val channelBase: String) {
        MESSAGE("msg", "chatbox_messages_v"),
        CALL("call", "chatbox_calls_v")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun systemDefault(kind: Kind): Uri = when (kind) {
        Kind.MESSAGE -> Settings.System.DEFAULT_NOTIFICATION_URI
        Kind.CALL -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_RINGTONE_URI
    }

    /** Chosen tone; null = silent. */
    fun tone(context: Context, kind: Kind): Uri? {
        val saved = prefs(context).getString("${kind.key}_uri", null)
        return when {
            saved == null || saved.isBlank() -> systemDefault(kind)
            saved == SILENT -> null
            else -> runCatching { Uri.parse(saved) }.getOrDefault(systemDefault(kind))
        }
    }

    fun vibrate(context: Context, kind: Kind): Boolean =
        prefs(context).getBoolean("${kind.key}_vibrate", true)

    fun channelId(context: Context, kind: Kind): String =
        kind.channelBase + prefs(context).getInt("${kind.key}_ver", 1)

    /** Human-readable tone name ("Silent", "Default", or the sound's title). */
    fun title(context: Context, kind: Kind): String {
        val saved = prefs(context).getString("${kind.key}_uri", null)
        if (saved == SILENT) return "Silent"
        if (saved.isNullOrBlank()) return "Default"
        return runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(saved))?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Custom"
    }

    fun setTone(context: Context, kind: Kind, uri: Uri?) {
        val value = when {
            uri == null -> SILENT
            uri == systemDefault(kind) || uri == Settings.System.DEFAULT_NOTIFICATION_URI ||
                uri == Settings.System.DEFAULT_RINGTONE_URI -> ""
            else -> uri.toString()
        }
        if (prefs(context).getString("${kind.key}_uri", "") == value) return
        prefs(context).edit().putString("${kind.key}_uri", value).apply()
        rebuild(context, kind)
    }

    fun setVibrate(context: Context, kind: Kind, on: Boolean) {
        if (vibrate(context, kind) == on) return
        prefs(context).edit().putBoolean("${kind.key}_vibrate", on).apply()
        rebuild(context, kind)
    }

    /** Drop the frozen channel and move to a new id; the notifier recreates it with the new sound. */
    private fun rebuild(context: Context, kind: Kind) {
        val p = prefs(context)
        val old = channelId(context, kind)
        p.edit().putInt("${kind.key}_ver", p.getInt("${kind.key}_ver", 1) + 1).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(old)
            }
        }
        ChatboxNotifier.ensureChannels(context)
    }

    /** Short preview of the chosen tone (returns the playing Ringtone so callers can stop it). */
    fun preview(context: Context, kind: Kind): android.media.Ringtone? {
        val uri = tone(context, kind) ?: return null
        return runCatching { RingtoneManager.getRingtone(context, uri)?.also { it.play() } }.getOrNull()
    }
}
