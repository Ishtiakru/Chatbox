package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminAuthStatus
import com.example.data.model.AdminRole
import com.example.data.model.SparkCommandProposal
import com.example.ui.components.ChatboxNoticeBanner
import com.example.ui.components.SignInRequiredCard
import com.example.ui.viewmodel.SparkAdminUiState

@Composable
fun AdminSignInGate(
    authBusy: Boolean,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        AdminHeader()
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SignInRequiredCard(
            title = "This space is locked.",
            body = "Verified Google sign-in and server-side Firestore security rules are required for administrator controls.",
            icon = Icons.Rounded.Lock,
            isBusy = authBusy,
            onSignIn = onSignIn,
            testTagPrefix = "admin"
        )
    }
}

@Composable
fun AdminScreen(
    uiState: SparkAdminUiState,
    onCheckAccess: () -> Unit,
    onVerifyGoogleAgain: () -> Unit,
    onCommandChange: (String) -> Unit,
    onPreviewCommand: () -> Unit,
    onApplyProposal: () -> Unit,
    onTargetEmailChange: (String) -> Unit,
    onTargetConfirmedChange: (Boolean) -> Unit,
    onInviteDelegate: () -> Unit,
    onRevokeDelegate: (String) -> Unit,
    onAcceptDelegation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1040.dp)
        ) {
            AdminHeader()
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 840.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        AdminControlsCard(
                            uiState = uiState,
                            onCheckAccess = onCheckAccess,
                            onVerifyGoogleAgain = onVerifyGoogleAgain,
                            onCommandChange = onCommandChange,
                            onPreviewCommand = onPreviewCommand,
                            onApplyProposal = onApplyProposal,
                            onTargetEmailChange = onTargetEmailChange,
                            onTargetConfirmedChange = onTargetConfirmedChange,
                            onInviteDelegate = onInviteDelegate,
                            onRevokeDelegate = onRevokeDelegate,
                            onAcceptDelegation = onAcceptDelegation,
                            modifier = Modifier.weight(1.15f)
                        )
                        AdminStudioPreviewCard(modifier = Modifier.weight(0.85f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AdminControlsCard(
                            uiState = uiState,
                            onCheckAccess = onCheckAccess,
                            onVerifyGoogleAgain = onVerifyGoogleAgain,
                            onCommandChange = onCommandChange,
                            onPreviewCommand = onPreviewCommand,
                            onApplyProposal = onApplyProposal,
                            onTargetEmailChange = onTargetEmailChange,
                            onTargetConfirmedChange = onTargetConfirmedChange,
                            onInviteDelegate = onInviteDelegate,
                            onRevokeDelegate = onRevokeDelegate,
                            onAcceptDelegation = onAcceptDelegation,
                            modifier = Modifier.fillMaxWidth()
                        )
                        AdminStudioPreviewCard(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "A SPACE FOR THE PEOPLE IN CHARGE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Admin studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "EXPERIMENTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "A thoughtful way to manage your space.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AdminControlsCard(
    uiState: SparkAdminUiState,
    onCheckAccess: () -> Unit,
    onVerifyGoogleAgain: () -> Unit,
    onCommandChange: (String) -> Unit,
    onPreviewCommand: () -> Unit,
    onApplyProposal: () -> Unit,
    onTargetEmailChange: (String) -> Unit,
    onTargetConfirmedChange: (Boolean) -> Unit,
    onInviteDelegate: () -> Unit,
    onRevokeDelegate: (String) -> Unit,
    onAcceptDelegation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authorized = uiState.status == AdminAuthStatus.AUTHORIZED

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = "SECURITY FIRST",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (authorized) "Administrator controls" else "This space is locked.",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (uiState.error.isNotBlank()) {
                ChatboxNoticeBanner(text = uiState.error, isError = true)
            }
            if (uiState.notice.isNotBlank()) {
                ChatboxNoticeBanner(text = uiState.notice)
            }

            when (uiState.status) {
                AdminAuthStatus.IDLE, AdminAuthStatus.CHECKING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Checking administrator access…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AdminAuthStatus.INVITED -> {
                    ChatboxNoticeBanner(
                        text = "You have an invitation for delegated access. Verify your Google account again before accepting."
                    )
                    OutlinedButton(
                        onClick = onVerifyGoogleAgain,
                        enabled = !uiState.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_reauth_button")
                    ) {
                        Text("Verify Google account again")
                    }
                    Button(
                        onClick = onAcceptDelegation,
                        enabled = !uiState.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_accept_button")
                    ) {
                        Text("Accept delegated access")
                    }
                }
                AdminAuthStatus.FORBIDDEN, AdminAuthStatus.ERROR -> {
                    Text(
                        text = "This account is not an administrator or invited recipient. If this is the owner account, verify Google sign-in again before checking access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onVerifyGoogleAgain,
                        enabled = !uiState.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_reauth_button")
                    ) {
                        Text("Verify Google account again")
                    }
                    OutlinedButton(
                        onClick = onCheckAccess,
                        enabled = !uiState.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_recheck_button")
                    ) {
                        Text("Check again")
                    }
                }
                AdminAuthStatus.AUTHORIZED -> {
                    ChatboxNoticeBanner(
                        text = "Experimental Spark admin: welcome text, home color, and mobile menu position are supported. Verify your Google account again before saving changes."
                    )
                    OutlinedButton(
                        onClick = onVerifyGoogleAgain,
                        enabled = !uiState.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_reauth_button")
                    ) {
                        Text("Verify Google account again")
                    }

                    Text(
                        text = "Change request",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Examples: হোম পেজটা ল্যাভেন্ডার কালার করো · মেনুবার উপরে আনো · menu: bottom · theme: default · welcome: Hello everyone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.commandDraft,
                        onValueChange = onCommandChange,
                        enabled = !uiState.busy,
                        placeholder = { Text("welcome: Hello everyone") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_command_input")
                    )
                    Button(
                        onClick = onPreviewCommand,
                        enabled = !uiState.busy && uiState.commandDraft.trim().isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("admin_preview_button")
                    ) {
                        Text("Preview change")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    when (val proposal = uiState.proposal) {
                        null -> Unit
                        SparkCommandProposal.Unsupported -> {
                            Text(
                                text = "Unsupported command. Use one of the examples shown.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            val summary = when (proposal) {
                                is SparkCommandProposal.SetWelcome -> "Set welcome: ${proposal.value}"
                                is SparkCommandProposal.SetTheme -> "Set theme: ${proposal.value}"
                                is SparkCommandProposal.SetMenu -> "Set menu: ${proposal.value}"
                                SparkCommandProposal.Unsupported -> ""
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "PREVIEW — NOT YET APPLIED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Button(
                                        onClick = onApplyProposal,
                                        enabled = !uiState.busy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("admin_apply_button")
                                    ) {
                                        Text("Confirm change")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    if (uiState.role == AdminRole.OWNER) {
                        Text(
                            text = "Invite a Gmail as a delegate",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You keep owner access. The recipient must sign in and accept; no email is sent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = uiState.targetEmail,
                            onValueChange = onTargetEmailChange,
                            enabled = !uiState.busy,
                            singleLine = true,
                            placeholder = { Text("name@gmail.com") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_delegate_email_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.busy) {
                                    onTargetConfirmedChange(!uiState.targetConfirmed)
                                }
                                .minimumInteractiveComponentSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.targetConfirmed,
                                onCheckedChange = onTargetConfirmedChange,
                                enabled = !uiState.busy,
                                modifier = Modifier.testTag("admin_delegate_confirm_checkbox")
                            )
                            Text(
                                text = "I confirm this delegated access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OutlinedButton(
                            onClick = onInviteDelegate,
                            enabled = !uiState.busy && uiState.targetConfirmed && uiState.targetEmail.trim().isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("admin_invite_button")
                        ) {
                            Text("Save invitation")
                        }

                        Text(
                            text = "Delegations (first 100)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.delegations.isEmpty()) {
                            Text(
                                text = "No delegations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.delegations.forEach { delegation ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${delegation.email} · ${delegation.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (delegation.status != "revoked") {
                                        OutlinedButton(
                                            onClick = { onRevokeDelegate(delegation.email) },
                                            enabled = !uiState.busy,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("admin_revoke_${delegation.email}")
                                        ) {
                                            Text("Revoke")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Delegates cannot grant or revoke access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "An email address alone will never grant admin access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AdminStudioPreviewCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WHAT IS SUPPORTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Just say what you need.",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "LIMITED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "When configured, you can preview and confirm a public welcome message, home color, and navigation bar position. Arbitrary actions are not supported.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "✳", color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "হোম পেজটা ল্যাভেন্ডার কালার করো",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "These limited commands are deterministic and not AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Admin access",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "One owner with approved delegates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Activity history",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Full audit history is not available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
