package com.example.ui.screens

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatboxCatalog
import com.example.data.model.AvatarStyle
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxThread
import com.example.ui.ChatboxRoot
import com.example.ui.theme.ChatboxTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
    }

    @Test
    fun `app launches directly into chatbox without login gate or demo naming`() {
        composeRule.setContent {
            ChatboxRoot()
        }

        // Advance past the 1200ms launch splash (both main looper & compose clock)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.onAllNodesWithTag("nav_chatbox").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Must NOT show any login screen or Demo label
        composeRule.onAllNodesWithText("Continue with Google", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Demo", substring = true, ignoreCase = true).assertCountEquals(0)

        // Bottom nav shows Chatbox, Saira, Calls, Profile
        composeRule.onNodeWithTag("nav_chatbox").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_saira").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_calls").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_profile").assertIsDisplayed()

        // Inbox shows all users' DMs directly (wait for DataStore emission to settle)
        composeRule.onNodeWithTag("chatbox_dm_root").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            composeRule.onAllNodesWithTag("chatbox_thread_sazzad").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chatbox_inbox_lazy_list").performScrollToKey("sazzad")
        composeRule.onNodeWithTag("chatbox_thread_sazzad").assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_inbox_lazy_list").performScrollToKey("saira")
        composeRule.onNodeWithTag("chatbox_thread_saira").assertIsDisplayed()
        composeRule.onNodeWithTag("chatbox_inbox_lazy_list").performScrollToKey("maya")
        composeRule.onNodeWithTag("chatbox_thread_maya").assertIsDisplayed()

        // Profile screen must NOT have Log out / Sign out
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Log out", ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Sign out", ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `chatbox dm thread and details screen support all messenger lite features from screenshots`() {
        var recordedReportReason: String? = null
        val callLogs = mutableListOf<ChatboxCallEntry>()

        composeRule.setContent {
            var threads by remember { mutableStateOf(ChatboxCatalog.defaultThreads) }
            var activeThreadId by remember { mutableStateOf<String?>("sazzad") }
            val messages = remember {
                mutableStateMapOf<String, List<ChatboxMessage>>().apply {
                    putAll(ChatboxCatalog.defaultSeeds)
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
                            val archived = ChatboxCatalog.archivedHistorySeeds[tid].orEmpty()
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
}
