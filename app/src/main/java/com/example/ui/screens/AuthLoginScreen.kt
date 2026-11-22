package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.auth.openGmailInbox
import com.example.data.auth.sendPasswordResetToGmail
import com.example.data.model.WorldLanguages
import com.example.ui.theme.ChatboxLavenderLogo
import com.example.ui.theme.ChatboxLoginCanvasBottom
import com.example.ui.theme.ChatboxLoginCanvasTop
import com.example.ui.theme.ChatboxLoginCardBorder
import com.example.ui.theme.ChatboxLoginPrimary
import com.example.ui.theme.ChatboxLoginTextPrimary
import com.example.ui.theme.ChatboxLoginTextSecondary
import kotlinx.coroutines.launch

@Composable
fun AuthLoginScreen(
    language: String,
    authReady: Boolean,
    authLoading: Boolean,
    onLanguageSelected: (String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bn = WorldLanguages.isBengali(language)
    val selectedLanguageOption = remember(language) { WorldLanguages.findByCode(language) }

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showSignInInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ChatboxLoginCanvasTop, ChatboxLoginCanvasBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showLanguageDialog = true }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("language_selector"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = ChatboxLoginTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedLanguageOption.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChatboxLoginTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(id = R.string.cd_language_chevron),
                        tint = ChatboxLoginTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(0.58f, fill = true)
                    .heightIn(min = 24.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.4.dp, ChatboxLavenderLogo.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.chatbox_logo_lavender),
                        contentDescription = stringResource(id = R.string.cd_chatbox_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .clip(CircleShape)
                    )
                }
                Text(
                    text = "Chatbox",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = ChatboxLavenderLogo,
                    modifier = Modifier.testTag("login_logo_title")
                )
            }

            Spacer(
                modifier = Modifier
                    .weight(0.52f, fill = true)
                    .heightIn(min = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick = {
                        if (authReady && !authLoading) {
                            onGoogleSignIn()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ChatboxLoginCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("google_account_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (bn) "Google অ্যাকাউন্ট দিয়ে চালিয়ে যান" else "Continue with Google account",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChatboxLoginTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 14.dp)
                        )
                        IconButton(
                            onClick = { showSignInInfoDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_in_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(id = R.string.cd_sign_in_info),
                                tint = ChatboxLoginTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onGoogleSignIn,
                    enabled = authReady && !authLoading,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChatboxLavenderLogo,
                        contentColor = Color.White,
                        disabledContainerColor = ChatboxLavenderLogo.copy(alpha = 0.65f),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (!authReady) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (bn) "অ্যাকাউন্ট যাচাই হচ্ছে…" else "Checking account…",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    } else if (authLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (bn) "সাইন ইন হচ্ছে…" else "Logging in…",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    } else {
                        Text(
                            text = if (bn) "Google দিয়ে লগ ইন" else "Log in with Google",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("forgot_password_button")
                ) {
                    Text(
                        text = if (bn) "পাসওয়ার্ড ভুলে গেছেন?" else "Forgot password?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ChatboxLoginTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onGoogleSignIn,
                    enabled = authReady && !authLoading,
                    shape = CircleShape,
                    border = BorderStroke(1.2.dp, ChatboxLoginPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ChatboxLoginPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_new_account_button")
                ) {
                    Text(
                        text = if (bn) "নতুন অ্যাকাউন্ট তৈরি করুন" else "Create new account",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .heightIn(min = 24.dp)
            )
        }
    }

    if (showLanguageDialog) {
        WorldLanguagePickerDialog(
            selectedCode = selectedLanguageOption.code,
            isBengali = bn,
            onSelect = { code ->
                showLanguageDialog = false
                onLanguageSelected(code)
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordGmailDialog(
            isBengali = bn,
            onDismiss = { showForgotPasswordDialog = false },
            onSendReset = { email -> sendPasswordResetToGmail(email) },
            onOpenGmail = { openGmailInbox(context) },
            scope = scope
        )
    }

    if (showSignInInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSignInInfoDialog = false },
            title = {
                Text(
                    text = if (bn) "Chatbox লগ ইন তথ্য" else "Chatbox Sign-In",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (bn) {
                        "Chatbox নিরাপদ Google সাইন-ইন ব্যবহার করে। “নতুন অ্যাকাউন্ট তৈরি করুন” বা “Google দিয়ে লগ ইন” চাপলে আপনার ফোনের Google অ্যাকাউন্ট দিয়েই স্বয়ংক্রিয়ভাবে প্রোফাইল তৈরি বা লগ ইন হবে। আলাদা পাসওয়ার্ড বা নম্বর-ভিত্তিক লগ ইন চাইলে Firebase Console → Authentication → Sign-in method থেকে Email/Password বা Phone চালু করতে হবে।"
                    } else {
                        "Chatbox uses secure Google Sign-In via Android Credential Manager. Tapping “Create new account” or “Log in with Google” lets you choose or add a Google account on your phone to create or restore your profile. To enable separate Email/Password or Phone authentication, turn on those providers in Firebase Console → Authentication → Sign-in method."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showSignInInfoDialog = false }) {
                    Text(if (bn) "ঠিক আছে" else "Got it")
                }
            }
        )
    }
}

@Composable
private fun WorldLanguagePickerDialog(
    selectedCode: String,
    isBengali: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            WorldLanguages.all
        } else {
            WorldLanguages.all.filter { option ->
                option.nativeLabel.lowercase().contains(q) ||
                    option.englishName.lowercase().contains(q) ||
                    option.countryName.lowercase().contains(q) ||
                    option.code.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isBengali) "সব দেশের ভাষা নির্বাচন করুন" else "Choose language",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(id = R.string.search_languages_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(id = R.string.cd_search_languages)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(id = R.string.cd_clear_search)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_search_input")
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(filteredLanguages, key = { it.code }) { option ->
                        val selected = option.code == selectedCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(option.code) }
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("language_option_${option.code}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.nativeLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (selected) ChatboxLavenderLogo else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${option.englishName} · ${option.countryName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = ChatboxLavenderLogo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBengali) "বন্ধ করুন" else "Close")
            }
        }
    )
}

@Composable
private fun ForgotPasswordGmailDialog(
    isBengali: Boolean,
    onDismiss: () -> Unit,
    onSendReset: suspend (String) -> Result<Unit>,
    onOpenGmail: () -> Boolean,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var gmailInput by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sentToEmail by remember { mutableStateOf<String?>(null) }

    fun submitReset() {
        if (busy) return
        val trimmed = gmailInput.trim()
        busy = true
        errorMessage = null
        scope.launch {
            val result = onSendReset(trimmed)
            busy = false
            result.onSuccess {
                sentToEmail = trimmed
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Could not send password reset link."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Email,
                contentDescription = null,
                tint = ChatboxLavenderLogo
            )
        },
        title = {
            Text(
                text = if (isBengali) "Gmail-এ পাসওয়ার্ড রিসেট পাঠান" else "Reset password via Gmail",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isBengali) {
                        "আপনার ফোনের Gmail ঠিকানা দিন। রিসেট লিংক সরাসরি আপনার Gmail ইনবক্সে পাঠানো হবে।"
                    } else {
                        "Enter your phone's Gmail address below to receive a Firebase password reset email directly in Gmail."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = gmailInput,
                    onValueChange = {
                        gmailInput = it
                        errorMessage = null
                        sentToEmail = null
                    },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(if (isBengali) "আপনার Gmail" else "Your Gmail address") },
                    placeholder = { Text("name@gmail.com") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { submitReset() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_password_email_input")
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!sentToEmail.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ChatboxLavenderLogo.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isBengali) {
                                    "$sentToEmail ঠিকানায় পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে। Gmail চেক করুন।"
                                } else {
                                    "Password reset link sent to $sentToEmail. Check your Gmail inbox."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = { onOpenGmail() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_gmail_button")
                            ) {
                                Text(if (isBengali) "Gmail খুলুন" else "Open Gmail")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitReset() },
                enabled = !busy && gmailInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ChatboxLavenderLogo),
                modifier = Modifier.testTag("send_reset_email_button")
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isBengali) "রিসেট লিংক পাঠান" else "Send reset link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBengali) "বাতিল" else "Cancel")
            }
        }
    )
}
