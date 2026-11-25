package com.example.data.model

import androidx.annotation.Keep
import com.example.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

object ChatboxConstants {
    val APP_VERSION: String get() = BuildConfig.VERSION_NAME
    const val GITHUB_RELEASES_BASE_URL = "https://github.com/Ishtiakru/Chatbox/releases"

    const val MAX_DISPLAY_NAME_LENGTH = 80
    const val MAX_ABOUT_LENGTH = 140
    const val MAX_PHONE_LENGTH = 24
    const val MAX_MESSAGE_BODY_LENGTH = 2000
    const val MAX_WELCOME_LENGTH = 160
    const val MAX_UPDATE_VERSION_LENGTH = 32
    const val MAX_UPDATE_NOTE_LENGTH = 240
    const val MAX_UPDATE_URL_LENGTH = 512

    val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,128}$")
    val VALID_SHORT_ID_REGEX = Regex("^[0-9]{6}$")
    val VALID_PHONE_KEY_REGEX = Regex("^[0-9]{7,15}$")
    val VALID_GMAIL_REGEX = Regex("^[a-z0-9][a-z0-9._%+-]{0,63}@gmail\\.com$")

    val BOOTSTRAPPED_OWNER_EMAILS = setOf(
        "sojibsheikhuk@gmail.com"
    )
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class CoreAppTab {
    CHATBOX, SAIRA, CALLS, PROFILE, ADMIN
}

sealed interface AppTab {
    data class Core(val tab: CoreAppTab) : AppTab
    data class Custom(val menu: SparkCustomMenu) : AppTab
}

enum class CustomMenuIconName(val key: String) {
    SPARKLES("sparkles"),
    BELL("bell"),
    MEGAPHONE("megaphone"),
    BOOK("book"),
    STAR("star"),
    INFO("info"),
    GRID("grid");

    companion object {
        fun fromKey(raw: String?): CustomMenuIconName =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SPARKLES
    }
}

enum class AvatarStyle {
    SAIRA, PEACH, LAVENDER, YELLOW, BLUE
}

enum class DisappearingOption(
    val key: String,
    val label: String,
    val shortLabel: String
) {
    OFF("off", "Off", "Off"),
    HOURS_24("24h", "24 hours", "24h"),
    DAYS_7("7d", "7 days", "7d"),
    DAYS_90("90d", "90 days", "90d");

    companion object {
        fun fromKey(key: String?): DisappearingOption =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OFF
    }
}

@Serializable
enum class ChatboxMessageKind {
    TEXT,
    MISSED_AUDIO_CALL,
    MISSED_VIDEO_CALL,
    CALL_COMPLETED,
    IMAGE,
    VOICE
}

@Serializable
data class ChatboxThread(
    val id: String,
    val name: String,
    val shortId: String = "",
    val phoneNumber: String = "",
    val role: String = "Active now",
    val connectionLabel: String = "You're connected on Chatbox",
    val locationText: String = "",
    val workBio: String = "",
    val avatarLabel: String = "",
    val style: AvatarStyle = AvatarStyle.LAVENDER,
    val label: String? = null,
    val online: Boolean = true,
    val isGroup: Boolean = false,
    val members: List<String> = emptyList(),
    val unreadCount: Int = 0,
    val readReceiptsEnabled: Boolean = true,
    val disappearingKey: String = "off",
    val e2eeVerified: Boolean = false,
    val historyLocked: Boolean = false,
    val historyUnlocked: Boolean = false,
    val quickReaction: String = "",
    /** Real Firebase uid of the peer (blank = local-only thread). */
    val peerUid: String = "",
    /** Firestore conversations/{id} this thread is synced with (blank = local-only). */
    val conversationId: String = ""
)

@Serializable
data class ChatboxMessage(
    val id: String = "",
    val from: String, // "me" | "them" | "system"
    val senderName: String = "",
    val text: String,
    val time: String,
    val kind: ChatboxMessageKind = ChatboxMessageKind.TEXT,
    val imageUri: String? = null,
    val audioPath: String? = null,
    val audioDurationMs: Long? = null,
    val isRestoredHistory: Boolean = false
)

@Serializable
data class ChatboxCallEntry(
    val id: String,
    val threadId: String,
    val peerName: String,
    val isVideo: Boolean = false,
    val isMissed: Boolean = false,
    val timeLabel: String,
    val durationLabel: String = ""
)

@Serializable
data class ScheduledCallEntry(
    val id: String,
    val threadId: String,
    val peerName: String,
    val isVideo: Boolean = false,
    val scheduledForLabel: String
)

@Serializable
data class ChatboxReportEntry(
    val id: String,
    val threadId: String,
    val peerName: String,
    val reason: String,
    val note: String = "",
    val timeLabel: String = ""
)

@Serializable
data class LocalUserProfile(
    val uid: String = "local_user",
    val displayName: String = "Chatbox User",
    val shortId: String = "482910",
    val about: String = "Hey there! I am using Chatbox.",
    val phoneNumber: String = ""
)

@Serializable
data class LocalSairaSessionData(
    val id: String,
    val title: String,
    val lastPreview: String,
    val updatedAtMs: Long,
    val exchanges: List<LocalSairaExchange> = emptyList()
)

@Serializable
data class LocalSairaExchange(
    val id: String,
    val question: String,
    val answer: String
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val shortId: String = "",
    val about: String = ""
)

@Keep
@IgnoreExtraProperties
data class UserPrivateProfile(
    val phoneNumber: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val createdBy: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationMs: Long? = null,
    @ServerTimestamp val sentAt: Timestamp? = null,
    val pendingWrites: Boolean = false
)

data class MessagesPage(
    val messages: List<LiveMessage>,
    val hasMore: Boolean
)

@Keep
@IgnoreExtraProperties
data class SparkDelegation(
    val email: String = "",
    val uid: String = "",
    val invitedBy: String = "",
    val status: String = "invited",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SparkAppearance(
    val theme: String = "default", // "default" | "lavender"
    val menu: String = "bottom" // "bottom" | "top"
)

@Keep
@IgnoreExtraProperties
data class SparkAppUpdate(
    val active: Boolean = false,
    val version: String = "",
    val note: String = "",
    val downloadUrl: String = ""
)

@Keep
@IgnoreExtraProperties
data class SparkCustomMenu(
    val id: String = "",
    val label: String = "",
    val icon: String = "sparkles",
    val title: String = "",
    val body: String = ""
) {
    val iconEnum: CustomMenuIconName
        get() = CustomMenuIconName.fromKey(icon)
}

data class SparkDynamicConfig(
    val appTitle: String = "Chatbox",
    val homeAnnouncement: String = "",
    val customMenus: List<SparkCustomMenu> = emptyList()
)

data class AdminAiUpdatePlan(
    val summary: String,
    val theme: String? = null,
    val menuPosition: String? = null,
    val welcomeBanner: String? = null,
    val appTitle: String? = null,
    val homeAnnouncement: String? = null,
    val addOrUpdateMenu: SparkCustomMenu? = null,
    val removeMenuId: String? = null,
    val appUpdate: SparkAppUpdate? = null
)

data class QeLocalReleaseArchive(
    val version: String,
    val filename: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val note: String,
    val downloadUrl: String
)

enum class AdminAuthStatus {
    IDLE,
    CHECKING,
    OWNER,
    DELEGATE,
    INVITED_DELEGATE,
    UNCLAIMED_OWNER,
    DENIED,
    ERROR
}

data class AiExchange(
    val id: String,
    val question: String,
    val answer: String,
    val isStreaming: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class SairaChatSession(
    @DocumentId val id: String = "",
    val title: String = "Saira Chat",
    val lastPreview: String = "",
    val exchangeCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SairaCompanionMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestampMs: Long
)

/** Result of searching a phone number / 6-digit ID on the server (WhatsApp-style). */
sealed interface PeerSearchState {
    data object Idle : PeerSearchState
    data object Searching : PeerSearchState
    data class Found(val profile: UserProfile) : PeerSearchState
    data class NotFound(val message: String) : PeerSearchState
    data object NeedsLogin : PeerSearchState
    data class Failed(val message: String) : PeerSearchState
}
