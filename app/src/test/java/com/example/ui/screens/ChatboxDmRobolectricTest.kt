package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatboxCatalog
import com.example.data.model.AvatarStyle
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxMessageKind
import com.example.data.model.ChatboxThread
import com.example.data.repository.AppPreferencesRepository
import com.example.ui.ChatboxRoot
import com.example.ui.theme.ChatboxTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class ChatboxDmRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun initFirebase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context) ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:testapp")
                    .setApiKey("test-api-key-only-for-robolectric")
                    .setProjectId("test-project-id")
                    .build()
            )
        }
        runBlocking {
            val prefs = AppPreferencesRepository(context)
            prefs.saveChatboxThreads(emptyList())
            prefs.saveChatboxMessages(emptyMap())
            prefs.clearChatboxCallLogs()
            prefs.saveScheduledCalls(emptyList())
        }
    }

    private fun advancePastSplashAndEnterChatbox() {
        // the launch intro runs ≈ 2.7 s of animation clock
        composeRule.waitUntil(timeoutMillis = 12_000) {
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
    }

    @Test
    fun `app launches to chatbox via skip or login without demo or sign out inside app`() {
        composeRule.setContent {
            ChatboxRoot()
        }

        advancePastSplashAndEnterChatbox()

        // Must NOT show any Demo label or Sign out / Log out button inside the app
        composeRule.onAllNodesWithText("Demo", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Sign out", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Log out", substring = true, ignoreCase = true).assertCountEquals(0)

        // Bottom nav shows Chatbox, Saira, Calls, Profile
        composeRule.onNodeWithTag("nav_chatbox").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_saira").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_calls").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_profile").assertIsDisplayed()

        // Inbox starts clean with zero demo users, no top ID chip, no PersonAdd button, and compact search icon
        composeRule.onNodeWithTag("chatbox_dm_root").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            composeRule.onAllNodesWithTag("chatbox_inbox_lazy_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_search_toggle_button").assertIsDisplayed()
        composeRule.onAllNodesWithTag("chatbox_own_short_id_chip").assertCountEquals(0)
        composeRule.onAllNodesWithTag("chatbox_new_dm_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("chatbox_thread_sazzad").assertCountEquals(0)
        composeRule.onAllNodesWithTag("chatbox_thread_saira").assertCountEquals(0)
        composeRule.onAllNodesWithTag("chatbox_thread_maya").assertCountEquals(0)
        composeRule.onNodeWithText("No users added yet").assertIsDisplayed()

        // Profile screen now has Log out option added as requested
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Log out", ignoreCase = true).assertCountEquals(1)
    }

    @Test
    fun `chatbox dm thread and details screen support all messenger lite features from screenshots`() {
        var recordedReportReason: String? = null
        val callLogs = mutableListOf<ChatboxCallEntry>()

        val fixtureThreads = listOf(
            ChatboxThread(
                id = "sazzad",
                name = "Sazzad Jony",
                shortId = "102938",
                role = "Active now",
                connectionLabel = "You're connected on Chatbox",
                locationText = "Lives in Dhaka, Bangladesh",
                workBio = "Software Engineer at Dhaka",
                avatarLabel = "SJ",
                style = AvatarStyle.LAVENDER,
                online = true,
                unreadCount = 0,
                historyLocked = true,
                readReceiptsEnabled = true
            ),
            ChatboxThread(
                id = "maya",
                name = "Maya Rahman",
                shortId = "554433",
                role = "Active 8m ago",
                connectionLabel = "You're connected on Chatbox",
                avatarLabel = "MR",
                style = AvatarStyle.PEACH,
                online = true,
                unreadCount = 1,
                readReceiptsEnabled = true
            )
        )
        val fixtureMessages = mapOf(
            "sazzad" to listOf(
                ChatboxMessage(
                    id = "sz_1",
                    from = "them",
                    senderName = "Sazzad Jony",
                    text = "kire asoc",
                    time = "11:45 AM"
                ),
                ChatboxMessage(
                    id = "sz_2",
                    from = "them",
                    senderName = "Sazzad Jony",
                    text = "Missed audio call",
                    time = "2:10 PM",
                    kind = ChatboxMessageKind.MISSED_AUDIO_CALL
                )
            )
        )
        val fixtureArchived = mapOf(
            "sazzad" to listOf(
                ChatboxMessage(
                    id = "sz_arch_1",
                    from = "them",
                    senderName = "Sazzad Jony",
                    text = "Bhai ajke free achos?",
                    time = "Yesterday"
                )
            )
        )

        composeRule.setContent {
            var threads by remember { mutableStateOf(fixtureThreads) }
            var activeThreadId by remember { mutableStateOf<String?>("sazzad") }
            val messages = remember {
                mutableStateMapOf<String, List<ChatboxMessage>>().apply {
                    putAll(fixtureMessages)
                }
            }
            var nicknames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            var mutedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
            var blockedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
            var globalQuickReaction by remember { mutableStateOf("👍") }

            ChatboxTheme {
                ChatboxDmScreen(
                    threads = threads,
                    activeThreadId = activeThreadId,
                    messagesByThread = messages,
                    peerNicknames = nicknames,
                    mutedThreadIds = mutedIds,
                    blockedThreadIds = blockedIds,
                    globalQuickReaction = globalQuickReaction,
                    backupPinHint = "123456",
                    publicWelcomeBanner = "Welcome to Chatbox",
                    isOnline = true,
                    appUpdateActive = false,
                    onSelectThread = { id ->
                        activeThreadId = id
                        threads = threads.map { if (it.id == id) it.copy(unreadCount = 0) else it }
                    },
                    onSendMessage = { tid, body, timeStr ->
                        val nextMsg = ChatboxMessage(
                            id = "m_${(messages[tid]?.size ?: 0) + 1}",
                            from = "me",
                            senderName = "You",
                            text = body,
                            time = timeStr
                        )
                        messages[tid] = (messages[tid].orEmpty()) + nextMsg
                        // Simulate the peer opening the chat right away (read receipt).
                        threads = threads.map { if (it.id == tid) it.copy(peerSeenMessageId = nextMsg.id) else it }
                    },
                    onSendImage = { _, _ -> },
                    onSendVoice = { _, _, _ -> },
                    onCreateDirectThread = { name, identifier ->
                        val newId = "dm_${threads.size + 1}"
                        threads = listOf(
                            ChatboxThread(
                                id = newId,
                                name = name,
                                shortId = identifier.ifBlank { "998877" },
                                avatarLabel = name.take(2).uppercase(),
                                style = AvatarStyle.LAVENDER,
                                online = true
                            )
                        ) + threads
                        newId
                    },
                    onCreateGroupWithContact = { baseId, groupName, members ->
                        val base = threads.firstOrNull { it.id == baseId }
                        val allMembers = (listOfNotNull(base?.name) + members).distinct()
                        val resolvedName = groupName.ifBlank { "${base?.name?.substringBefore(" ")} & Friends" }
                        val gid = "grp_${threads.size + 1}"
                        threads = listOf(
                            ChatboxThread(
                                id = gid,
                                name = resolvedName,
                                role = "Group · ${allMembers.size + 1} members",
                                connectionLabel = "Group conversation on Chatbox",
                                avatarLabel = resolvedName.take(2).uppercase(),
                                style = AvatarStyle.LAVENDER,
                                label = "GROUP",
                                isGroup = true,
                                members = allMembers
                            )
                        ) + threads
                        gid
                    },
                    onMarkThreadUnread = { tid, unread ->
                        threads = threads.map {
                            if (it.id == tid) it.copy(unreadCount = if (unread) 1 else 0) else it
                        }
                    },
                    onToggleMute = { tid, muted ->
                        mutedIds = if (muted) mutedIds + tid else mutedIds - tid
                    },
                    onUpdateNickname = { tid, nick ->
                        nicknames = if (nick.isBlank()) nicknames - tid else nicknames + (tid to nick)
                    },
                    onThreadQuickReactionChange = { tid, emoji ->
                        threads = threads.map { if (it.id == tid) it.copy(quickReaction = emoji) else it }
                        globalQuickReaction = emoji
                    },
                    onToggleE2eeVerified = { tid, verified ->
                        threads = threads.map { if (it.id == tid) it.copy(e2eeVerified = verified) else it }
                    },
                    onDisappearingChange = { tid, key ->
                        threads = threads.map { if (it.id == tid) it.copy(disappearingKey = key) else it }
                    },
                    onToggleReadReceipts = { tid, enabled ->
                        threads = threads.map {
                            if (it.id == tid) it.copy(readReceiptsEnabled = enabled) else it
                        }
                    },
                    onUnlockHistoryWithPin = { tid, pin ->
                        if (pin == "123456") {
                            threads = threads.map { if (it.id == tid) it.copy(historyLocked = false) else it }
                            val archived = fixtureArchived[tid].orEmpty()
                            if (archived.isNotEmpty()) {
                                messages[tid] = archived + messages[tid].orEmpty()
                            }
                            true
                        } else {
                            false
                        }
                    },
                    onUpdateBackupPin = {},
                    onToggleBlock = { tid ->
                        blockedIds = if (tid in blockedIds) blockedIds - tid else blockedIds + tid
                    },
                    onSubmitReport = { _, _, reason, _ ->
                        recordedReportReason = reason
                    },
                    onLogCall = { tid, peerName, isVideo, isMissed, durationLabel ->
                        callLogs += ChatboxCallEntry(
                            id = "c_${callLogs.size + 1}",
                            threadId = tid,
                            peerName = peerName,
                            isVideo = isVideo,
                            isMissed = isMissed,
                            timeLabel = "Just now",
                            durationLabel = durationLabel
                        )
                    },
                    onAppUpdateClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1) Open Sazzad Jony DM from inbox
        composeRule.onNodeWithTag("chatbox_thread_sazzad").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_thread_screen").assertIsDisplayed()

        // Verify thread elements matching screenshot 2
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_profile_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You're connected on Chatbox").assertIsDisplayed()
        composeRule.onNodeWithText("Lives in Dhaka, Bangladesh").assertIsDisplayed()

        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_view_profile_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("thread_view_profile_button").assertIsDisplayed()

        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_e2ee_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("thread_e2ee_learn_more").assertIsDisplayed()

        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_restore_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restore_chat_history_card").assertIsDisplayed()

        // Scroll LazyColumn to seeded messages
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("sz_1")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("kire asoc").assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("sz_2")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("call_event_bubble").assertIsDisplayed()

        // 2) View profile & E2EE Learn more dialogs
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_view_profile_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("thread_view_profile_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_e2ee_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("thread_e2ee_learn_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Got it").performClick()
        composeRule.waitForIdle()

        // 3) Restore chat history with wrong PIN then correct PIN (123456)
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_restore_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restore_enter_pin_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restore_pin_input").performTextInput("0000")
        composeRule.onNodeWithTag("restore_pin_confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Incorrect PIN", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("intro_restore_sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restore_enter_pin_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("restore_pin_input").performTextInput("123456")
        composeRule.onNodeWithTag("restore_pin_confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("restore_chat_history_card").assertCountEquals(0)
        composeRule.onNodeWithTag("chatbox_thread_lazy_list").performScrollToKey("sz_arch_1")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bhai ajke free achos?").assertIsDisplayed()

        // 4) Send a message & verify mini read-receipt avatar appears
        composeRule.onNodeWithTag("chatbox_message_input").performTextInput("ki obostha")
        composeRule.onNodeWithTag("chatbox_send_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ki obostha").assertIsDisplayed()
        composeRule.onNodeWithTag("read_receipt_mini_avatar").assertIsDisplayed()
        // Tapping the message shows a small "Seen" label.
        composeRule.onNodeWithText("ki obostha").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Seen").assertIsDisplayed()

        // 5) Open Details screen (Screenshot 1)
        composeRule.onNodeWithTag("chatbox_thread_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_dm_details_screen").assertIsDisplayed()

        // Quick circle actions
        composeRule.onNodeWithTag("chatbox_details_call").assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_details_video").assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_details_profile").assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_details_mute").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Muted").assertIsDisplayed()

        // Quick reaction customization -> change to 🔥
        composeRule.onNodeWithTag("chatbox_details_quick_reaction").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_reaction_option_🔥").performClick()
        composeRule.waitForIdle()

        // Nickname customization
        composeRule.onNodeWithTag("chatbox_details_nicknames").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nickname_dialog_input").performTextInput("Jony Bhai")
        composeRule.onNodeWithTag("nickname_dialog_save").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Jony Bhai").assertCountEquals(2)

        // Verify end-to-end encryption
        composeRule.onNodeWithTag("chatbox_details_verify_e2ee").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("e2ee_verify_confirm_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Verified").assertIsDisplayed()

        // Disappearing messages -> 24 hours
        composeRule.onNodeWithTag("chatbox_details_disappearing").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("disappearing_option_24h").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("24h").assertIsDisplayed()

        // Read receipts Off -> hides mini avatar back in thread
        composeRule.onNodeWithTag("chatbox_details_read_receipts").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Off").assertIsDisplayed()

        // Report conversation
        composeRule.onNodeWithTag("chatbox_details_report").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("report_submit_button").performClick()
        composeRule.waitForIdle()
        assertEquals("Spam or unwanted messages", recordedReportReason)

        // Block & Unblock toggle
        composeRule.onNodeWithTag("chatbox_details_block").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Unblock", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_details_block").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Navigate back to thread -> verify read receipt mini avatar is now hidden and quick reaction updated
        composeRule.onNodeWithTag("chatbox_details_back").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("read_receipt_mini_avatar").assertCountEquals(0)

        // Open Details again -> Mark as unread returns to inbox with unread dot
        composeRule.onNodeWithTag("chatbox_thread_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_details_mark_unread").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_inbox_lazy_list").performScrollToKey("sazzad")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("unread_dot_sazzad", useUnmergedTree = true).assertIsDisplayed()

        // Create group with Sazzad
        composeRule.onNodeWithTag("chatbox_thread_sazzad").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_thread_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_details_create_group").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_group_name_input").performTextInput("Dhaka Squad")
        composeRule.onNodeWithTag("create_group_confirm_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Dhaka Squad").assertCountEquals(2)
        assertTrue(callLogs.isEmpty())
    }

    @Test
    fun `searching and adding friend by 6-digit id or name works cleanly without permission denied error`() {
        composeRule.setContent {
            ChatboxRoot()
        }

        advancePastSplashAndEnterChatbox()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            composeRule.onAllNodesWithTag("chatbox_search_toggle_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Tap compact lavender Search icon button
        composeRule.onAllNodesWithTag("chatbox_own_short_id_chip").assertCountEquals(0)
        composeRule.onNodeWithTag("chatbox_search_toggle_button").performClick()
        composeRule.waitForIdle()

        // Search by 6-digit ID: WhatsApp-style server lookup. Signed out in tests,
        // so the card must ask the user to log in — and NO fake thread is created.
        composeRule.onNodeWithTag("chatbox_search_input").performTextInput("685331")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            composeRule.onAllNodesWithTag("chatbox_search_login_required").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chatbox_search_login_required").assertIsDisplayed()
        composeRule.onAllNodesWithTag("chatbox_search_add_user_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("Permission denied", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Chatbox Friend", substring = true).assertCountEquals(0)

        // Name search never invents a contact; it explains how to find new people.
        composeRule.onNodeWithTag("chatbox_search_input").performTextClearance()
        composeRule.onNodeWithTag("chatbox_search_input").performTextInput("Rahim")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_search_hint").assertIsDisplayed()
        composeRule.onAllNodesWithTag("chatbox_search_add_user_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("chatbox_thread_screen").assertCountEquals(0)
    }

    @Test
    fun `calls menu shows quick actions number or id search add button and zero demo users`() {
        composeRule.setContent {
            ChatboxRoot()
        }

        advancePastSplashAndEnterChatbox()
        composeRule.onNodeWithTag("nav_calls").performClick()
        composeRule.waitForIdle()

        // Zero demo users in Calls
        composeRule.onAllNodesWithText("Sazzad", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Maya", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Arif", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Nora", substring = true, ignoreCase = true).assertCountEquals(0)

        // WhatsApp Calls style quick actions + Recent + Search bar + small Add button + FAB
        composeRule.onNodeWithTag("calls_action_call").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_action_schedule").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_action_keypad").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_action_favorites").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_add_user_button").assertIsDisplayed()
        composeRule.onNodeWithTag("calls_new_call_fab").assertIsDisplayed()
        composeRule.onNodeWithText("Recent").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("calls_empty_state").performScrollTo().assertIsDisplayed()

        // An ID that belongs to nobody must NOT create a fake "Chatbox Friend" contact or call.
        composeRule.onNodeWithTag("calls_search_input").performTextInput("774411")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calls_add_user_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("calls_added_user_dialog").assertCountEquals(0)
        composeRule.onAllNodesWithTag("calls_active_call_dialog").assertCountEquals(0)
        composeRule.onAllNodesWithText("Chatbox Friend", substring = true).assertCountEquals(0)
    }

    @Test
    fun `standard manifest has no notification listener and saira explains full edition`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, flags)
        }

        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        // Standard edition must NOT declare the notification listener: Play
        // Protect's enhanced fraud protection hard-blocks such sideloaded APKs.
        // (The listener only ships in the separate "-full" APK.)
        assertTrue(
            "Standard APK must not declare BIND_NOTIFICATION_LISTENER_SERVICE",
            pkgInfo.services.orEmpty().none {
                it.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE
            }
        )
        assertTrue(
            "WAKE_LOCK only belongs to the full edition",
            !requested.contains(Manifest.permission.WAKE_LOCK)
        )
        assertTrue(
            "Saira Automation (accessibility) only ships in the full edition",
            pkgInfo.services.orEmpty().none { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE }
        )
        assertTrue("SEND_SMS only belongs to the full edition", !requested.contains(Manifest.permission.SEND_SMS))
        assertEquals(
            "allowBackup must be false",
            0,
            (pkgInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_ALLOW_BACKUP
        )

        // Saira Auto-Reply tab shows the WhatsApp/Messenger card with access status
        composeRule.setContent {
            ChatboxRoot()
        }
        advancePastSplashAndEnterChatbox()
        composeRule.onNodeWithTag("nav_saira").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saira_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("saira_subtab_rules").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("saira_play_protect_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("saira_full_edition_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `auth login screen matches screenshot controls and supports log in create account and forgot password`() {
        var enteredViaSkip = false
        var loggedInIdentifier: String? = null
        var createdAccountName: String? = null
        var resetIdentifier: String? = null
        var selectedLang = "en-US"

        composeRule.setContent {
            var lang by remember { mutableStateOf(selectedLang) }
            ChatboxTheme {
                AuthLoginScreen(
                    selectedLanguageCode = lang,
                    initialIdentifier = "",
                    onLanguageSelected = { code ->
                        lang = code
                        selectedLang = code
                    },
                    onBackSkipLogin = { enteredViaSkip = true },
                    onLoginWithCredentials = { id, pwd ->
                        if (id.isBlank() || pwd.length < 4) {
                            Result.failure(IllegalArgumentException("Enter valid credentials"))
                        } else {
                            loggedInIdentifier = id
                            Result.success(Unit)
                        }
                    },
                    onCreateAccount = { name, id, pwd ->
                        if (name.isBlank() || id.isBlank() || pwd.length < 4) {
                            Result.failure(IllegalArgumentException("Fill all fields"))
                        } else {
                            createdAccountName = name
                            Result.success(Unit)
                        }
                    },
                    onResetPassword = { _, id, newPwd ->
                        if (id.isBlank() || newPwd.length < 4) {
                            Result.failure(IllegalArgumentException("Invalid reset input"))
                        } else {
                            resetIdentifier = id
                            Result.success("Password updated. You can now log in.")
                        }
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Verify all UI elements matching the screenshot
        composeRule.onNodeWithTag("login_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("login_language_button").assertIsDisplayed()
        composeRule.onNodeWithText("English (US)").assertIsDisplayed()
        composeRule.onNodeWithTag("login_identifier_input").assertIsDisplayed()
        composeRule.onNodeWithTag("login_password_input").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Log in").assertIsDisplayed()
        composeRule.onNodeWithTag("login_forgot_password_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_create_account_button").performScrollTo().assertIsDisplayed()

        // Language picker switches language
        composeRule.onNodeWithTag("login_language_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_language_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("বাংলা (বাংলাদেশ)").performClick()
        composeRule.waitForIdle()
        assertEquals("bn-BD", selectedLang)

        // Info icon toggles identifier hint
        composeRule.onNodeWithTag("login_identifier_info_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_identifier_hint_banner").performScrollTo().assertIsDisplayed()

        // Forgot password dialog resets password
        composeRule.onNodeWithTag("login_forgot_password_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forgot_password_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("forgot_identifier_input").performTextInput("01712345678")
        composeRule.onNodeWithTag("forgot_new_password_input").performTextInput("pass1234")
        composeRule.onNodeWithTag("forgot_reset_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("01712345678", resetIdentifier)

        // Create new account dialog works
        composeRule.onNodeWithTag("login_create_account_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_account_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("create_account_name_input").performTextInput("Istiak Ahmed")
        composeRule.onNodeWithTag("create_account_identifier_input").performTextInput("istiak@gmail.com")
        composeRule.onNodeWithTag("create_account_password_input").performTextInput("secret99")
        composeRule.onNodeWithTag("create_account_submit_button").performClick()
        composeRule.waitForIdle()
        assertEquals("Istiak Ahmed", createdAccountName)

        // Direct Log in works
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("pass1234")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("01712345678", loggedInIdentifier)

        // Back button skips login directly into Chatbox
        composeRule.onNodeWithTag("login_back_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(enteredViaSkip)
    }

    @Test
    fun `login has no google or sms option and create account opens with any password`() {
        var created: Triple<String, String, String>? = null
        var reset: Pair<String, String>? = null

        composeRule.setContent {
            ChatboxTheme {
                AuthLoginScreen(
                    selectedLanguageCode = "en-US",
                    initialIdentifier = "",
                    onLanguageSelected = {},
                    onBackSkipLogin = null,
                    onLoginWithCredentials = { _, _ -> Result.failure(IllegalArgumentException("wrong")) },
                    onCreateAccount = { n, id, pwd ->
                        created = Triple(n, id, pwd)
                        Result.success(Unit)
                    },
                    onResetPassword = { _, id, pwd ->
                        reset = id to pwd
                        Result.success("ok")
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Release builds: no skip, no Google, no SMS-code option.
        composeRule.onAllNodesWithTag("login_back_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("login_phone_otp_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("Continue with Google").assertCountEquals(0)

        // Wrong password is rejected and shown.
        composeRule.onNodeWithTag("login_identifier_input").performTextInput("01712345678")
        composeRule.onNodeWithTag("login_password_input").performTextInput("anything")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("wrong").assertExists()

        // Create account: number + any password, no SMS step, no Google button.
        composeRule.onNodeWithTag("login_create_account_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("create_account_google_button").assertCountEquals(0)
        composeRule.onNodeWithTag("create_account_name_input").performTextInput("Istiak")
        composeRule.onNodeWithTag("create_account_identifier_input").performTextInput("01712345678")
        composeRule.onNodeWithTag("create_account_password_input").performTextInput("secret99")
        composeRule.onNodeWithTag("create_account_submit_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("phone_otp_dialog").assertCountEquals(0)
        assertEquals(Triple("Istiak", "01712345678", "secret99"), created)

        // Forgot password goes straight to the Gmail check (in the ViewModel).
        composeRule.onNodeWithTag("login_forgot_password_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forgot_new_password_input").performTextInput("newpass1")
        composeRule.onNodeWithTag("forgot_reset_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("01712345678" to "newpass1", reset)
    }

    @Test
    fun `gmail popup blocks the app until a gmail is linked`() {
        var linked = false
        var calls = 0
        composeRule.setContent {
            ChatboxTheme {
                if (!linked) {
                    GmailRequiredDialog(
                        needsPassword = false,
                        onLinkGmail = { _, _ ->
                            calls++
                            if (calls == 1) {
                                Result.failure(IllegalStateException("একটা Gmail বেছে নিন।"))
                            } else {
                                linked = true
                                Result.success("me@gmail.com")
                            }
                        }
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        // Opened the Gmail list automatically; user dismissed it -> popup stays, no fallback yet.
        assertEquals(1, calls)
        composeRule.onNodeWithTag("gmail_required_dialog").assertExists()
        composeRule.onNodeWithText("একটা Gmail বেছে নিন।").assertExists()
        composeRule.onAllNodesWithTag("gmail_manual_input").assertCountEquals(0)
        composeRule.onNodeWithTag("gmail_required_pick_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, calls)
        assertTrue(linked)
    }

    @Test
    fun `gmail popup offers typing the gmail when the device picker fails`() {
        var saved: String? = null
        composeRule.setContent {
            ChatboxTheme {
                GmailRequiredDialog(
                    needsPassword = false,
                    knownGmails = listOf("old@gmail.com", "second@gmail.com"),
                    onLinkGmail = { _, _ -> Result.failure(IllegalStateException("Gmail যুক্ত করা যায়নি।\n(কোড: X)")) },
                    onSaveTypedGmail = { g, _ ->
                        if (g.endsWith("@gmail.com")) { saved = g; Result.success(g) }
                        else Result.failure(IllegalArgumentException("সঠিক Gmail লিখুন"))
                    }
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gmail_known_list").assertExists()
        composeRule.onNodeWithTag("gmail_manual_input").performScrollTo().performTextInput("me@gmail.com")
        composeRule.onNodeWithTag("gmail_manual_save_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("me@gmail.com", saved)
    }
}
