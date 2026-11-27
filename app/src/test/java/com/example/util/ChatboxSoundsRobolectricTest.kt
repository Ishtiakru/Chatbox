package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatboxSoundsRobolectricTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    @Test
    fun defaultsKeepTheOriginalChannels() {
        assertEquals("chatbox_messages_v1", ChatboxSounds.channelId(ctx, ChatboxSounds.Kind.MESSAGE))
        assertEquals("chatbox_calls_v1", ChatboxSounds.channelId(ctx, ChatboxSounds.Kind.CALL))
        assertEquals("Default", ChatboxSounds.title(ctx, ChatboxSounds.Kind.MESSAGE))
        ChatboxNotifier.ensureChannels(ctx)
        assertNotNull(nm.getNotificationChannel("chatbox_messages_v1"))
    }

    @Test
    fun choosingSilentRecreatesTheChannelWithoutSound() {
        ChatboxNotifier.ensureChannels(ctx)
        ChatboxSounds.setTone(ctx, ChatboxSounds.Kind.MESSAGE, null)
        assertEquals("Silent", ChatboxSounds.title(ctx, ChatboxSounds.Kind.MESSAGE))
        assertEquals("chatbox_messages_v2", ChatboxSounds.channelId(ctx, ChatboxSounds.Kind.MESSAGE))
        assertNull(nm.getNotificationChannel("chatbox_messages_v1"))
        assertNull(nm.getNotificationChannel("chatbox_messages_v2")!!.sound)
    }

    @Test
    fun customRingtoneAndVibrationApplyToTheCallChannel() {
        val tone = Uri.parse("content://media/internal/audio/media/42")
        ChatboxSounds.setTone(ctx, ChatboxSounds.Kind.CALL, tone)
        ChatboxSounds.setVibrate(ctx, ChatboxSounds.Kind.CALL, false)
        val ch = nm.getNotificationChannel(ChatboxSounds.channelId(ctx, ChatboxSounds.Kind.CALL))!!
        assertEquals(tone, ch.sound)
        assertFalse(ch.shouldVibrate())
        assertEquals("chatbox_calls_v3", ch.id)
    }
}
