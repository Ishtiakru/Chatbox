package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.SparkCommandProposal
import com.example.data.model.UserProfile
import com.example.data.model.WorldLanguages
import com.example.data.repository.ChatboxFirestoreProvider
import com.example.data.repository.SparkAdminCommands
import com.example.media.VoicePlaybackState
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.LiveThreadPane
import com.example.ui.theme.ChatboxTheme
import com.example.ui.viewmodel.LiveThreadState
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.PersistentCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp")
class OfflineSmsAndCoreFeaturesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `firestore provider configures persistent offline cache and reuses singleton instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val first = ChatboxFirestoreProvider.getInstance(context)
        val second = ChatboxFirestoreProvider.getInstance(context)

        assertSame(first, second)
        assertTrue(first.firestoreSettings.cacheSettings is PersistentCacheSettings)
    }

    @Test
    fun `offline live thread disables calls, media, and reactions while keeping text SMS enabled`() {
        val conversation = Conversation(
            id = "conv_1",
            participants = listOf("user_a", "user_b"),
            createdBy = "user_a",
            status = "active"
        )
        val queuedMessage = LiveMessage(
            id = "m1",
            senderId = "user_a",
            body = "Offline SMS test",
            pendingWrites = true
        )

        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                LiveThreadPane(
                    conversation = conversation,
                    currentUserId = "user_a",
                    peerProfile = UserProfile(uid = "user_b", displayName = "Rahima"),
                    threadState = LiveThreadState(
                        selectedId = "conv_1",
                        isOnline = false,
                        messageDraft = "Hello offline",
                        messages = listOf(queuedMessage),
                        messagesFromCache = true
                    ),
                    voicePlaybackState = VoicePlaybackState(),
                    isBlocked = false,
                    profileReady = true,
                    quickReaction = "💜",
                    showBackButton = true,
                    onBack = {},
                    onOpenDetails = {},
                    onOpenCalls = {},
                    onCloseThreadSearch = {},
                    onThreadSearchQueryChange = {},
                    onAcceptRequest = {},
                    onCloseOrDecline = {},
                    onLoadOlder = {},
                    onMessageDraftChange = {},
                    onAppendEmoji = {},
                    onCreateCameraUri = { null },
                    onAttachImage = {},
                    onClearAttachedImage = {},
                    onStartVoiceRecording = {},
                    onStopVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onClearPendingVoice = {},
                    onToggleVoicePlayback = { _, _, _ -> },
                    onAttachmentError = {},
                    onSendMessage = {},
                    onSendQuickReaction = {},
                    onUnblockPeer = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Offline SMS-only indicators and text composer stay active
        composeRule.onNodeWithTag("live_offline_cache_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sms_only_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("live_message_input").assertIsEnabled()
        composeRule.onNodeWithTag("live_send_button").assertIsEnabled()

        // Calls disabled; media, voice, emoji, and quick reaction hidden offline
        composeRule.onNodeWithTag("live_thread_voice_call_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("live_thread_video_call_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("live_attach_gallery_button").assertDoesNotExist()
        composeRule.onNodeWithTag("live_attach_camera_button").assertDoesNotExist()
        composeRule.onNodeWithTag("live_voice_record_button").assertDoesNotExist()
        composeRule.onNodeWithTag("live_emoji_button").assertDoesNotExist()
        composeRule.onNodeWithTag("live_quick_reaction_button").assertDoesNotExist()
    }

    @Test
    fun `online live thread restores calls, media, voice, and quick reaction controls`() {
        val conversation = Conversation(
            id = "conv_2",
            participants = listOf("user_a", "user_b"),
            createdBy = "user_a",
            status = "active"
        )

        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                LiveThreadPane(
                    conversation = conversation,
                    currentUserId = "user_a",
                    peerProfile = UserProfile(uid = "user_b", displayName = "Rahima"),
                    threadState = LiveThreadState(
                        selectedId = "conv_2",
                        isOnline = true,
                        messageDraft = ""
                    ),
                    voicePlaybackState = VoicePlaybackState(),
                    isBlocked = false,
                    profileReady = true,
                    quickReaction = "💜",
                    showBackButton = true,
                    onBack = {},
                    onOpenDetails = {},
                    onOpenCalls = {},
                    onCloseThreadSearch = {},
                    onThreadSearchQueryChange = {},
                    onAcceptRequest = {},
                    onCloseOrDecline = {},
                    onLoadOlder = {},
                    onMessageDraftChange = {},
                    onAppendEmoji = {},
                    onCreateCameraUri = { null },
                    onAttachImage = {},
                    onClearAttachedImage = {},
                    onStartVoiceRecording = {},
                    onStopVoiceRecording = {},
                    onCancelVoiceRecording = {},
                    onClearPendingVoice = {},
                    onToggleVoicePlayback = { _, _, _ -> },
                    onAttachmentError = {},
                    onSendMessage = {},
                    onSendQuickReaction = {},
                    onUnblockPeer = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("live_thread_voice_call_button").assertIsEnabled()
        composeRule.onNodeWithTag("live_thread_video_call_button").assertIsEnabled()
        composeRule.onNodeWithTag("live_attach_gallery_button").assertIsEnabled()
        composeRule.onNodeWithTag("live_attach_camera_button").assertIsEnabled()
        composeRule.onNodeWithTag("live_voice_record_button").assertIsEnabled()
        composeRule.onNodeWithTag("live_emoji_button").assertIsEnabled()
        composeRule.onNodeWithTag("live_quick_reaction_button").assertIsEnabled()
    }

    @Test
    fun `calls screen shows offline notice and returns to conversations`() {
        var returned = false
        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                CallsScreen(
                    isOnline = false,
                    onBackToConversations = { returned = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("calls_offline_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_back_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(returned)
    }

    @Test
    fun `world languages and Spark admin command parsing work accurately`() {
        assertEquals("bn-BD", WorldLanguages.normalizeCode("bn"))
        assertTrue(WorldLanguages.isBengali("bn-BD"))
        assertTrue(WorldLanguages.all.size >= 45)


        assertEquals(
            SparkCommandProposal.SetTheme("lavender"),
            SparkAdminCommands.parseSparkCommand("হোম পেজটা ল্যাভেন্ডার কালার করো")
        )
        assertEquals(
            SparkCommandProposal.SetMenu("top"),
            SparkAdminCommands.parseSparkCommand("menu: top")
        )
        assertEquals(
            SparkCommandProposal.SetWelcome("Welcome to Chatbox!"),
            SparkAdminCommands.parseSparkCommand("welcome: Welcome to Chatbox!")
        )
        assertTrue(SparkAdminCommands.validSparkTarget("sojibsheikhuk@gmail.com"))
    }
}
