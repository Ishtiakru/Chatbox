package com.example.saira

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiFallbackProviderKind
import com.example.ui.ChatboxRoot
import com.example.ui.screens.SairaAiScreen
import com.example.ui.theme.ChatboxTheme
import com.example.ui.viewmodel.ChatboxAppViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SairaFeatureSuiteRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun awaitCondition(timeoutMs: Long = 4_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
            if (predicate()) return
            Thread.sleep(20)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for async ViewModel/DataStore condition", predicate())
    }

    @Test
    fun `saira ai screen supports rule creation live auto reply testing logs clear and backup ai config`() {
        val vm = ChatboxAppViewModel(app)
        vm.clockHourProvider = { 14 }
        vm.onlineOverride = false

        composeRule.setContent {
            val rules by vm.sairaRules.collectAsState()
            val logs by vm.sairaAutoReplyLogs.collectAsState()
            val oneMin by vm.oneMinAutoReplyEnabled.collectAsState()
            val notif by vm.notifAutoReplyEnabled.collectAsState()
            val loc by vm.liveLocationEnabled.collectAsState()
            val kind by vm.aiFallbackProviderKind.collectAsState()
            val key by vm.aiFallbackApiKey.collectAsState()
            val url by vm.aiFallbackBaseUrl.collectAsState()
            val model by vm.aiFallbackModel.collectAsState()
            val threads by vm.chatboxThreads.collectAsState()

            ChatboxTheme {
                SairaAiScreen(
                    currentUserId = "",
                    isOnline = true,
                    appUpdateActive = false,
                    onAppUpdateClick = {},
                    oneMinAutoReplyEnabled = oneMin,
                    onChangeOneMinAutoReply = vm::setOneMinAutoReplyEnabled,
                    notifAutoReplyEnabled = notif,
                    onChangeNotifAutoReply = vm::setNotifAutoReplyEnabled,
                    liveLocationEnabled = loc,
                    onChangeLiveLocation = vm::setLiveLocationEnabled,
                    rules = rules,
                    onSaveRules = vm::saveSairaRules,
                    autoReplyLogs = logs,
                    onClearAutoReplyLogs = vm::clearSairaAutoReplyLogs,
                    fallbackKindId = kind,
                    fallbackApiKey = key,
                    fallbackBaseUrl = url,
                    fallbackModel = model,
                    onSaveFallbackConfig = vm::saveAiFallbackConfig,
                    sessionRepo = vm.sairaSessionRepo,
                    localSessions = emptyList(),
                    dmThreads = threads,
                    onTestAutoReply = { sender, platform, msg ->
                        vm.testAutoReply(sender, platform, msg, recordLog = true)
                    },
                    onSimulateIncomingDm = { tid, body ->
                        vm.simulateIncomingChatboxMessage(tid, body, triggerAutoReplyAfterWait = true)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Verify starter prompts & time greeting render on Chat tab
        composeRule.onNodeWithTag("saira_starter_prompt_0").assertIsDisplayed()
        composeRule.onNodeWithTag("saira_prompt_input").assertIsDisplayed()
        composeRule.onNodeWithTag("saira_history_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Saved Saira Threads").assertIsDisplayed()
        composeRule.onNodeWithText("New Unlimited Thread").performClick()
        composeRule.waitForIdle()

        // Switch to Auto-Reply tab
        composeRule.onNodeWithTag("saira_subtab_rules").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("saira_play_protect_status").performScrollTo().assertIsDisplayed()

        // Configure Free Backup AI provider (Groq)
        composeRule.onNodeWithTag("saira_backup_config_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("${AiFallbackProviderKind.GROQ.label} API Key").performTextInput("gsk_test_key_123")
        composeRule.onNodeWithTag("save_backup_ai_button").performClick()
        composeRule.waitForIdle()
        awaitCondition { runBlocking { vm.prefs.aiFallbackApiKey.first() } == "gsk_test_key_123" }

        // Add a taught rule for Rahima
        composeRule.onNodeWithTag("saira_rule_contact_input").performScrollTo().performTextInput("Rahima")
        composeRule.onNodeWithTag("saira_rule_guidance_input").performScrollTo().performTextInput("একটু পর কল দেব")
        composeRule.onNodeWithTag("saira_save_rule_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        awaitCondition { vm.sairaRules.value.any { it.contactName == "Rahima" } }

        // The manual "test auto-reply" card was removed from the UI; the
        // auto-reply engine itself must still answer taught contacts.
        composeRule.onAllNodesWithTag("saira_test_autoreply_card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("saira_test_run_button").assertCountEquals(0)
        val engineResult = runBlocking {
            vm.testAutoReply("Rahima", SairaPlatform.ANY, "Ki koro?", recordLog = true)
        }
        assertTrue("Expected an auto-reply", engineResult.replied && engineResult.replyText.isNotBlank())
        awaitCondition { vm.sairaAutoReplyLogs.value.isNotEmpty() }
        composeRule.waitForIdle()

        // Clear logs button works
        composeRule.onNodeWithTag("saira_clear_logs_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        awaitCondition { vm.sairaAutoReplyLogs.value.isEmpty() }

        // Delete rule button works
        val rahimaRuleId = vm.sairaRules.value.first { it.contactName == "Rahima" }.id
        composeRule.onNodeWithTag("saira_rule_delete_$rahimaRuleId").performScrollTo().performClick()
        composeRule.waitForIdle()
        awaitCondition { vm.sairaRules.value.none { it.id == rahimaRuleId } }
    }

    @Test
    fun `all primary app tabs chatbox saira calls and profile navigate cleanly`() {
        composeRule.setContent {
            ChatboxRoot()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.onAllNodesWithTag("login_back_button").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("nav_chatbox").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("login_back_button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("login_back_button").performClick()
            composeRule.waitUntil(timeoutMillis = 3_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
                composeRule.onAllNodesWithTag("nav_chatbox").fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.waitForIdle()

        // Calls tab
        composeRule.onNodeWithTag("nav_calls").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calls_back_to_conversations").performScrollTo().assertIsDisplayed()

        // Profile tab & settings sheets
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        // Admin's layout: no Privacy / Chats rows; Edit picture + Edit name rows; Account = ID, Gmail, phone only.
        composeRule.onAllNodesWithTag("profile_row_privacy").assertCountEquals(0)
        composeRule.onAllNodesWithTag("profile_row_chats").assertCountEquals(0)
        // Header: no ID chip / Gmail / pencil (full editor) — those live in Account only.
        composeRule.onAllNodesWithTag("profile_copy_id_chip").assertCountEquals(0)
        composeRule.onAllNodesWithTag("profile_edit_button").assertCountEquals(0)
        composeRule.onNodeWithTag("profile_status_bubble").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_about_input").assertIsDisplayed()
        composeRule.onAllNodesWithTag("profile_name_input").assertCountEquals(0)
        composeRule.onAllNodesWithTag("profile_phone_input").assertCountEquals(0)
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_row_edit_profile_picture").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("profile_row_edit_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("profile_row_account").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_gmail_card").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_phone_input").assertIsDisplayed()
        composeRule.onAllNodesWithTag("profile_name_input").assertCountEquals(0)
        composeRule.onAllNodesWithTag("profile_about_input").assertCountEquals(0)
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_row_edit_name").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_name_input").assertIsDisplayed()
        composeRule.onAllNodesWithTag("profile_phone_input").assertCountEquals(0)
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        // Saira tab -> Auto-Reply card visible
        composeRule.onNodeWithTag("nav_saira").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("saira_subtab_rules").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("saira_play_protect_status").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("saira_test_autoreply_card").assertCountEquals(0)

        // Back to Chatbox tab
        composeRule.onNodeWithTag("nav_chatbox").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_dm_root").assertIsDisplayed()
    }
}
