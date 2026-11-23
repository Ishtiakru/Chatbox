package com.example

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppPreferencesRepository
import com.example.saira.SairaAssistantEngine
import com.example.saira.SairaNotificationRule
import com.example.saira.SairaPlatform
import com.example.ui.screens.SairaAiScreen
import com.example.ui.theme.ChatboxTheme
import com.example.ui.viewmodel.SairaAiUiState
import com.example.ui.viewmodel.SairaAiViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp")
class SairaAndNotificationsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `time of day greetings and night detection cover all 24 hours`() {
        assertEquals("Good Morning", SairaAssistantEngine.currentTimeGreeting(6))
        assertEquals("Good Morning", SairaAssistantEngine.currentTimeGreeting(11))
        assertEquals("Good Afternoon", SairaAssistantEngine.currentTimeGreeting(13))
        assertEquals("Good Evening", SairaAssistantEngine.currentTimeGreeting(19))
        assertEquals("Good Evening", SairaAssistantEngine.currentTimeGreeting(23))

        assertTrue(SairaAssistantEngine.isNightTime(22))
        assertTrue(SairaAssistantEngine.isNightTime(2))
        assertFalse(SairaAssistantEngine.isNightTime(10))
        assertFalse(SairaAssistantEngine.isNightTime(18))
    }

    @Test
    fun `notification platform detection and targeted rule matching ignore strangers and disabled rules`() {
        assertEquals(SairaPlatform.WHATSAPP, SairaAssistantEngine.detectPlatform("com.whatsapp"))
        assertEquals(SairaPlatform.WHATSAPP, SairaAssistantEngine.detectPlatform("com.whatsapp.w4b"))
        assertEquals(SairaPlatform.MESSENGER, SairaAssistantEngine.detectPlatform("com.facebook.orca"))
        assertEquals(SairaPlatform.MESSENGER, SairaAssistantEngine.detectPlatform("com.facebook.mlite"))
        assertNull(SairaAssistantEngine.detectPlatform("com.example.random"))

        val rules = listOf(
            SairaNotificationRule(
                id = "r1",
                contactName = "Rahima",
                platform = SairaPlatform.WHATSAPP,
                taughtReplyGuidance = "Ami busy achi, pore call dibo",
                enabled = true
            ),
            SairaNotificationRule(
                id = "r2",
                contactName = "Karim",
                platform = SairaPlatform.ANY,
                taughtReplyGuidance = "Paused rule",
                enabled = false
            )
        )

        val matched = SairaAssistantEngine.matchNotificationRule(
            packageName = "com.whatsapp",
            senderTitle = "Rahima Akter",
            conversationTitle = null,
            rules = rules
        )
        assertNotNull(matched)
        assertEquals("r1", matched?.id)

        // Wrong platform should not match WhatsApp-only rule
        assertNull(
            SairaAssistantEngine.matchNotificationRule(
                packageName = "com.facebook.orca",
                senderTitle = "Rahima Akter",
                conversationTitle = null,
                rules = rules
            )
        )

        // Disabled rule should not match
        assertNull(
            SairaAssistantEngine.matchNotificationRule(
                packageName = "com.whatsapp",
                senderTitle = "Karim",
                conversationTitle = null,
                rules = rules
            )
        )

        // Untaught stranger should not match
        assertNull(
            SairaAssistantEngine.matchNotificationRule(
                packageName = "com.whatsapp",
                senderTitle = "Unknown Person",
                conversationTitle = null,
                rules = rules
            )
        )
    }

    @Test
    fun `dispatchNotificationReply extracts RemoteInput and fires PendingIntent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteInput = RemoteInput.Builder("key_text_reply").setLabel("Reply").build()
        val replyIntent = PendingIntent.getBroadcast(
            context,
            101,
            Intent("com.example.TEST_REPLY_ACTION"),
            PendingIntent.FLAG_MUTABLE
        )
        val action = Notification.Action.Builder(null, "Reply", replyIntent)
            .addRemoteInput(remoteInput)
            .build()
        val notification = Notification.Builder(context, "test")
            .setContentTitle("Rahima")
            .setContentText("Kemon acho?")
            .addAction(action)
            .build()

        val sbn = StatusBarNotification(
            "com.whatsapp",
            "com.whatsapp",
            1,
            null,
            1000,
            0,
            0,
            notification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        val dispatched = SairaAssistantEngine.dispatchNotificationReply(
            context = context,
            sbn = sbn,
            replyText = "Good Evening! Ami ekhon ektu busy."
        )
        assertTrue(dispatched)
    }

    @Test
    fun `SairaAiViewModel manages rules and persists settings to DataStore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferencesRepository(context)
        val vm = SairaAiViewModel(preferencesRepository = prefs)

        vm.updateRuleContactDraft("Rahima")
        vm.updateRulePlatformDraft(SairaPlatform.MESSENGER)
        vm.updateRuleGuidanceDraft("Tell her I am offline right now.")
        vm.addNotificationRule()
        vm.setNotificationAutoReply(true)
        vm.setChatboxOneMinuteAutoReply(false)

        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (prefs.sairaNotificationRules.first().isNotEmpty()) return@repeat
            Thread.sleep(50)
        }

        val savedRules = prefs.sairaNotificationRules.first()
        assertEquals(1, savedRules.size)
        assertEquals("Rahima", savedRules.first().contactName)
        assertEquals(SairaPlatform.MESSENGER, savedRules.first().platform)
        assertTrue(prefs.sairaNotifReplyEnabled.first())
        assertFalse(prefs.sairaChatboxOneMinEnabled.first())
    }

    @Test
    fun `SairaAiScreen stays quiet until user sends a message and disables send when offline`() {
        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                SairaAiScreen(
                    isSignedIn = true,
                    authBusy = false,
                    uiState = SairaAiUiState(
                        draft = "Hello Saira",
                        notificationRules = listOf(
                            SairaNotificationRule(
                                id = "r1",
                                contactName = "Rahima",
                                platform = SairaPlatform.WHATSAPP,
                                taughtReplyGuidance = "Call me later"
                            )
                        )
                    ),
                    isOnline = false,
                    onSignIn = {},
                    onDraftChange = {},
                    onSubmitQuestion = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Saira Assistant is quiet until you message first.").assertIsDisplayed()
        composeRule.onNodeWithTag("saira_offline_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("saira_ai_send_button").assertIsNotEnabled()

        // Switch to Auto-Reply tab and verify rule renders cleanly
        composeRule.onNodeWithTag("saira_tab_auto_reply").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rahima").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Call me later").performScrollTo().assertIsDisplayed()
    }
}
