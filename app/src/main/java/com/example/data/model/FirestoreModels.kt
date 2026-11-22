package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val displayName: String = ""
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
) {
    fun peerUid(currentUid: String): String =
        participants.firstOrNull { it != currentUid }.orEmpty()
}

@Keep
@IgnoreExtraProperties
data class LiveMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val body: String = "",
    @ServerTimestamp val sentAt: Timestamp? = null,
    val pendingWrites: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class SparkDelegation(
    @DocumentId val email: String = "",
    val uid: String = "",
    val invitedBy: String = "",
    val status: String = "invited",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SparkAppearance(
    val theme: String = "default",
    val menu: String = "bottom"
)

sealed interface SparkCommandProposal {
    data class SetWelcome(val value: String) : SparkCommandProposal
    data class SetTheme(val value: String) : SparkCommandProposal
    data class SetMenu(val value: String) : SparkCommandProposal
    data object Unsupported : SparkCommandProposal
}
