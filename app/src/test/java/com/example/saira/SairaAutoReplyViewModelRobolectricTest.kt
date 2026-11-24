package com.example.saira

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.ChatboxAppViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SairaAutoReplyViewModelRobolectricTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(app) ?: FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:testapp")
                    .setApiKey("test-api-key-only-for-robolectric")
                    .setProjectId("test-project-id")
                    .build()
            )
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
            if (predicate()) return
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for async ViewModel/DataStore condition", predicate())
    }

    private fun pumpLooper(ms: Long) {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun <T> runSuspendAndAwait(
        timeoutMs: Long = 5_000L,
        block: suspend () -> T
    ): T {
        val resultRef = AtomicReference<Result<T>?>(null)
        CoroutineScope(Dispatchers.Default).launch {
            resultRef.set(runCatching { block() })
        }
        awaitCondition(timeoutMs) { resultRef.get() != null }
        return resultRef.get()!!.getOrThrow()
    }

    @Test
    fun `saira direct dm uses ai when online and offline guide when offline`() {
        val vm = ChatboxAppViewModel(app)
        vm.clockHourProvider = { 10 }
        vm.onlineOverride = true
        vm.aiCompleter = { _, _, _ -> "হ্যালো! আমি Saira Live AI থেকে উত্তর দিচ্ছি ✨" }

        vm.sendChatboxMessage("saira", "Tell me a fun fact", "10:00 AM")
        awaitCondition {
            vm.chatboxMessages.value["saira"]?.any {
                it.from == "them" && it.text.contains("Saira Live AI")
            } == true
        }

        vm.onlineOverride = false
        vm.sendChatboxMessage("saira", "pin", "10:01 AM")
        awaitCondition {
            vm.chatboxMessages.value["saira"]?.any {
                it.from == "them" && it.text.contains("123456")
            } == true
        }
    }

    @Test
    fun `chatbox 10 second absence auto reply steps in continues multi turn and honors toggle block and mute`() {
        val vm = ChatboxAppViewModel(app)
        vm.autoReplyWaitMs = 50L
        vm.continuationDebounceMs = 30L
        vm.clockHourProvider = { 19 }
        vm.onlineOverride = false
        vm.setOneMinAutoReplyEnabled(true)
        vm.clearSairaAutoReplyLogs()
        awaitCondition { vm.oneMinAutoReplyEnabled.value && vm.sairaAutoReplyLogs.value.isEmpty() }

        // 1) User messages Sazzad -> peer doesn't reply -> Saira steps in with time greeting & name
        vm.sendChatboxMessage("sazzad", "Kire kothay tui?", "07:00 PM")
        awaitCondition {
            vm.chatboxMessages.value["sazzad"]?.any {
                it.from == "them" && it.senderName == "Saira" && it.text.contains("শুভ সন্ধ্যা")
            } == true
        }
        awaitCondition {
            vm.sairaAutoReplyLogs.value.any { it.contactName == "Sazzad Jony" }
        }

        val countAfterFirstAutoReply = vm.chatboxMessages.value["sazzad"].orEmpty().size

        // 2) Follow-up while Saira is already engaged -> continues conversation without repeating initial greeting
        vm.sendChatboxMessage("sazzad", "Ajke dekha hobe?", "07:01 PM")
        awaitCondition {
            vm.chatboxMessages.value["sazzad"].orEmpty().size >= countAfterFirstAutoReply + 2
        }
        val latestFollowUp = vm.chatboxMessages.value["sazzad"].orEmpty().last()
        assertEquals("Saira", latestFollowUp.senderName)
        assertFalse(latestFollowUp.text.startsWith("হ্যালো Chatbox User! শুভ সন্ধ্যা"))

        // 3) Disabled toggle -> MUST NOT auto-reply
        vm.setOneMinAutoReplyEnabled(false)
        awaitCondition { !vm.oneMinAutoReplyEnabled.value }
        val mayaBefore = vm.chatboxMessages.value["maya"].orEmpty().size
        vm.sendChatboxMessage("maya", "Hi Maya", "07:02 PM")
        pumpLooper(180)
        assertEquals(mayaBefore + 1, vm.chatboxMessages.value["maya"].orEmpty().size)

        // 4) Re-enable, but blocked thread -> MUST NOT auto-reply
        vm.setOneMinAutoReplyEnabled(true)
        vm.toggleThreadBlocked("arif")
        awaitCondition { vm.oneMinAutoReplyEnabled.value && "arif" in vm.chatboxBlockedThreads.value }
        val arifBefore = vm.chatboxMessages.value["arif"].orEmpty().size
        vm.sendChatboxMessage("arif", "Yo Arif", "07:03 PM")
        pumpLooper(180)
        assertEquals(arifBefore + 1, vm.chatboxMessages.value["arif"].orEmpty().size)
    }

    @Test
    fun `incoming dm auto reply enforces taught rules location permission consent and cancels on manual reply`() {
        val vm = ChatboxAppViewModel(app)
        vm.autoReplyWaitMs = 60L
        vm.clockHourProvider = { 9 }
        vm.onlineOverride = true
        vm.aiCompleter = { _, _, prompt ->
            assertTrue(prompt.contains("Sazzad Jony"))
            "আমি এখন রাস্তায় আছি, একটু পর ফোন করছি।"
        }
        vm.clearSairaAutoReplyLogs()
        vm.setOneMinAutoReplyEnabled(true)
        vm.setLiveLocationEnabled(false)
        vm.saveSairaRules(
            listOf(
                SairaNotificationRule(
                    id = "rule_sz",
                    contactName = "Sazzad Jony",
                    platform = SairaPlatform.ANY,
                    taughtReplyGuidance = "আমি এখন মিটিংয়ে আছি।",
                    enabled = true,
                    allowLocationShare = true,
                    useAiReply = true
                )
            )
        )
        awaitCondition {
            vm.oneMinAutoReplyEnabled.value &&
                !vm.liveLocationEnabled.value &&
                vm.sairaAutoReplyLogs.value.isEmpty() &&
                vm.sairaRules.value.any { it.id == "rule_sz" }
        }

        // Turn 1 test WITHOUT location permission / master toggle -> box message only, NO Maps link
        val turn1 = runSuspendAndAwait {
            vm.testAutoReply(
                senderName = "Sazzad Jony",
                platform = SairaPlatform.ANY,
                incomingText = "কোথায় আছো?",
                recordLog = true
            )
        }
        assertTrue(turn1.replied)
        assertTrue(turn1.replyText.contains("সুপ্রভাত"))
        assertTrue(turn1.replyText.contains("আমি এখন মিটিংয়ে আছি।"))
        assertFalse(turn1.includedLocation)
        assertFalse(turn1.replyText.contains("maps.google.com"))
        awaitCondition {
            vm.sairaAutoReplyLogs.value.any { it.contactName == "Sazzad Jony" }
        }

        // Grant permission + enable liveLocationEnabled + seed mock Location -> Turn 2 uses AI + attaches Maps link!
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locMgr = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocMgr = Shadows.shadowOf(locMgr)
        shadowLocMgr.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        @Suppress("DEPRECATION")
        shadowLocMgr.setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = 23.79250
                longitude = 90.40780
                accuracy = 15f
                time = System.currentTimeMillis()
            }
        )
        vm.setLiveLocationEnabled(true)
        awaitCondition { vm.liveLocationEnabled.value }

        val turn2 = runSuspendAndAwait {
            vm.testAutoReply(
                senderName = "Sazzad Jony",
                platform = SairaPlatform.ANY,
                incomingText = "তোমার লাইভ লোকেশন দাও, কোথায় আছো?",
                recordLog = true
            )
        }
        assertTrue(turn2.replied)
        assertTrue(turn2.usedAi)
        assertTrue(turn2.includedLocation)
        assertTrue(turn2.replyText.contains("https://maps.google.com/?q=23.79250,90.40780"))

        // Incoming DM auto-reply cancelled when owner replies manually before autoReplyWaitMs
        vm.setOneMinAutoReplyEnabled(true)
        awaitCondition { vm.oneMinAutoReplyEnabled.value }
        val mayaBefore = vm.chatboxMessages.value["maya"].orEmpty().size
        vm.autoReplyWaitMs = 220L
        vm.simulateIncomingChatboxMessage("maya", "Are you coming?", triggerAutoReplyAfterWait = true)
        awaitCondition { vm.chatboxMessages.value["maya"].orEmpty().size >= mayaBefore + 1 }
        // Disable 10s absence before owner sends manual reply so we assert incoming auto-reply is cancelled cleanly
        vm.setOneMinAutoReplyEnabled(false)
        awaitCondition { !vm.oneMinAutoReplyEnabled.value }
        vm.sendChatboxMessage("maya", "Yes, leaving now!", "09:10 AM")
        pumpLooper(300)
        val mayaMessagesAfter = vm.chatboxMessages.value["maya"].orEmpty()
        assertFalse(mayaMessagesAfter.drop(mayaBefore).any { it.senderName == "Saira" })
    }
}
