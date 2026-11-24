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
    DEMO, LIVE, SAIRA, CALLS, PROFILE, ADMIN
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

data class DemoContact(
    val id: String,
    val name: String,
    val role: String,
    val avatar: String,
    val style: AvatarStyle,
    val label: String? = null,
    val online: Boolean = false
)

@Serializable
data class DemoMessage(
    val from: String, // "me" | "them"
    val text: String,
    val time: String
)

enum class DemoModal {
    CALL, VIDEO, ATTACH, NEW_CHAT, ABOUT
}

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
