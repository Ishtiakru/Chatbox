package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.reauthenticateCurrentGoogleUser
import com.example.data.model.AdminAuthStatus
import com.example.data.model.AdminRole
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkCommandProposal
import com.example.data.model.SparkDelegation
import com.example.data.repository.SparkAdminCommands
import com.example.data.repository.SparkAdminRepository
import com.example.data.repository.friendlyFirestoreMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SparkAdminUiState(
    val status: AdminAuthStatus = AdminAuthStatus.IDLE,
    val role: AdminRole = AdminRole.NONE,
    val delegations: List<SparkDelegation> = emptyList(),
    val commandDraft: String = "",
    val proposal: SparkCommandProposal? = null,
    val targetEmail: String = "",
    val targetConfirmed: Boolean = false,
    val busy: Boolean = false,
    val notice: String = "",
    val error: String = ""
)

class SparkAdminViewModel(
    private val repository: SparkAdminRepository,
    private val currentUserId: String,
    private val currentUserEmail: String?
) : ViewModel() {

    private companion object {
        const val TAG = "SparkAdminVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    private val _uiState = MutableStateFlow(SparkAdminUiState())
    val uiState: StateFlow<SparkAdminUiState> = _uiState.asStateFlow()

    val welcomeBanner: StateFlow<String> = repository.observeSparkWelcome()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing spark welcome", error)
            emit("")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ""
        )

    val appearance: StateFlow<SparkAppearance> = repository.observeSparkAppearance()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing spark appearance", error)
            emit(SparkAppearance())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SparkAppearance()
        )

    fun checkAdminAccess() {
        if (_uiState.value.status == AdminAuthStatus.CHECKING) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = AdminAuthStatus.CHECKING,
                error = ""
            )
            val result = repository.checkAdminStatus(currentUserId, currentUserEmail)
            result.onSuccess { statusResult ->
                val delegations = if (
                    statusResult.status == AdminAuthStatus.AUTHORIZED &&
                    statusResult.role == AdminRole.OWNER
                ) {
                    repository.listDelegations().getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                _uiState.value = _uiState.value.copy(
                    status = statusResult.status,
                    role = statusResult.role,
                    delegations = delegations
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    status = AdminAuthStatus.ERROR,
                    error = friendlyFirestoreMessage(error)
                )
            }
        }
    }

    fun verifyGoogleAccountAgain(context: Context, credentialManager: CredentialManager) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = "", notice = "")
            val reauth = reauthenticateCurrentGoogleUser(context, credentialManager)
            reauth.onSuccess {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    notice = "Google verification completed. Checking server authorization…"
                )
                checkAdminAccess()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = error.localizedMessage ?: "Could not verify this Google account."
                )
            }
        }
    }

    fun updateCommand(text: String) {
        if (text.length <= 1000) {
            _uiState.value = _uiState.value.copy(
                commandDraft = text,
                proposal = null
            )
        }
    }

    fun previewCommand() {
        val draft = _uiState.value.commandDraft.trim()
        if (draft.isEmpty()) return
        val proposal = SparkAdminCommands.parseSparkCommand(draft)
        _uiState.value = _uiState.value.copy(
            proposal = proposal,
            error = "",
            notice = ""
        )
    }

    fun applyProposal() {
        val state = _uiState.value
        val proposal = state.proposal ?: return
        if (state.busy || state.status != AdminAuthStatus.AUTHORIZED || proposal is SparkCommandProposal.Unsupported) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = "", notice = "")
            val result = repository.applyProposal(currentUserId, proposal)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    proposal = null,
                    notice = "Change saved."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = friendlyFirestoreMessage(error)
                )
            }
        }
    }

    fun updateTargetEmail(value: String) {
        val clamped = value.take(128)
        val changed = clamped != _uiState.value.targetEmail
        _uiState.value = _uiState.value.copy(
            targetEmail = clamped,
            targetConfirmed = if (changed) false else _uiState.value.targetConfirmed
        )
    }

    fun setTargetConfirmed(confirmed: Boolean) {
        _uiState.value = _uiState.value.copy(targetConfirmed = confirmed)
    }

    fun inviteDelegate() {
        val state = _uiState.value
        if (state.busy || state.status != AdminAuthStatus.AUTHORIZED || state.role != AdminRole.OWNER || !state.targetConfirmed) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = "", notice = "")
            val result = repository.inviteDelegate(currentUserId, state.targetEmail)
            result.onSuccess {
                val updated = repository.listDelegations().getOrDefault(state.delegations)
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    targetEmail = "",
                    targetConfirmed = false,
                    delegations = updated,
                    notice = "Invitation saved; the recipient must sign in and accept. No email was sent."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    targetConfirmed = false,
                    error = friendlyFirestoreMessage(error)
                )
            }
        }
    }

    fun revokeDelegate(email: String) {
        val state = _uiState.value
        if (state.busy || state.status != AdminAuthStatus.AUTHORIZED || state.role != AdminRole.OWNER) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = "", notice = "")
            val result = repository.revokeDelegate(email)
            result.onSuccess {
                val updated = repository.listDelegations().getOrDefault(state.delegations)
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    delegations = updated,
                    notice = "Access revoked."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = friendlyFirestoreMessage(error)
                )
            }
        }
    }

    fun acceptDelegation() {
        val state = _uiState.value
        if (state.busy || state.status != AdminAuthStatus.INVITED) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = "", notice = "")
            val result = repository.acceptDelegation(currentUserId, currentUserEmail)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    status = AdminAuthStatus.AUTHORIZED,
                    role = AdminRole.DELEGATE,
                    notice = "Delegated access accepted."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = friendlyFirestoreMessage(error)
                )
            }
        }
    }
}
