package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.WorldLanguages
import com.example.ui.components.ChatboxSearchField
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.ChatboxAccentViolet
import com.example.ui.theme.ChatboxBorder
import com.example.ui.theme.ChatboxBorderLavender
import com.example.ui.theme.ChatboxErrorInk
import com.example.ui.theme.ChatboxInkMuted
import com.example.ui.theme.ChatboxInkSubtle
import com.example.ui.theme.ChatboxPrimary
import com.example.ui.theme.ChatboxPrimaryDark
import com.example.ui.theme.ChatboxSoftLavender
import com.example.ui.theme.ChatboxSoftLavenderStrong
import kotlinx.coroutines.launch

@Composable
fun AuthLoginScreen(
    languageCode: String,
    authLoading: Boolean,
    authError: String?,
    isOnline: Boolean,
    rememberMeEnabled: Boolean,
    rememberedPhone: String,
    onSaveRememberedPhone: (Boolean, String) -> Unit,
    onQueuePendingPhone: (String) -> Unit = {},
    onLanguageSelected: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onChooseGoogleAccount: () -> Unit = onGoogleSignIn,
    onSendPasswordReset: suspend (String) -> Result<Unit>
) {
    val bn = WorldLanguages.isBengali(languageCode)
    val selectedLang = remember(languageCode) {
        val norm = WorldLanguages.normalizeCode(languageCode)
        WorldLanguages.all.firstOrNull { it.code == norm } ?: WorldLanguages.all.first()
    }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    var phoneInput by remember(rememberedPhone) { mutableStateOf(rememberedPhone) }
    var passwordInput by remember { mutableStateOf("") }
    var rememberChecked by remember(rememberMeEnabled) { mutableStateOf(rememberMeEnabled) }
    var passwordVisible by remember { mutableStateOf(false) }
    var formNotice by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6F9FE), Color.White)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top language selector
            Surface(
                onClick = { showLanguageDialog = true },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("language_selector_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Choose language",
                        tint = ChatboxInkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${selectedLang.label} (${selectedLang.region})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChatboxInkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = ChatboxInkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Center login card area
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(88.dp)
                        .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.35f), CircleShape),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Image(
                        painter = painterResource(R.drawable.chatbox_logo_lavender),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF0F766E)
                )

                if (!isOnline) {
                    NoticeBanner(
                        text = if (bn) {
                            "ইন্টারনেট সংযোগ নেই। সাইন ইন করতে ইন্টারনেট সংযোগ প্রয়োজন।"
                        } else {
                            "No internet connection. Data connection is required to sign in."
                        },
                        isError = true
                    )
                }
                if (!authError.isNullOrBlank()) {
                    NoticeBanner(text = authError, isError = true)
                }
                if (!formNotice.isNullOrBlank()) {
                    NoticeBanner(text = formNotice!!)
                }

                // Phone + Password Helper Fields
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        val next = it.take(24)
                        phoneInput = next
                        formNotice = null
                        if (rememberChecked) onSaveRememberedPhone(true, next)
                    },
                    enabled = !authLoading && isOnline,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                    label = {
                        Text(if (bn) "মোবাইল নম্বর (+880...)" else "Phone number (+8801XXXXXXXXX)")
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_phone_input")
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it.take(64)
                        formNotice = null
                    },
                    enabled = !authLoading && isOnline,
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    label = { Text(if (bn) "পাসওয়ার্ড লিখুন" else "Enter password") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val next = !rememberChecked
                                rememberChecked = next
                                onSaveRememberedPhone(next, phoneInput)
                            }
                            .padding(end = 8.dp)
                    ) {
                        Checkbox(
                            checked = rememberChecked,
                            onCheckedChange = { next ->
                                rememberChecked = next
                                onSaveRememberedPhone(next, phoneInput)
                            }
                        )
                        Text(
                            text = if (bn) "আমাকে মনে রাখুন" else "Remember me",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChatboxInkMuted
                        )
                    }

                    TextButton(
                        onClick = { showForgotDialog = true },
                        modifier = Modifier.testTag("forgot_password_button")
                    ) {
                        Text(
                            text = if (bn) "পাসওয়ার্ড ভুলে গেছেন?" else "Forgot password?",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatboxPrimary
                        )
                    }
                }

                Button(
                    onClick = {
                        val cleanPhone = phoneInput.trim()
                        if (cleanPhone.length < 7) {
                            formNotice = if (bn) "সঠিক মোবাইল নম্বর দিন।" else "Enter a valid phone number."
                            return@Button
                        }
                        if (passwordInput.length < 6) {
                            formNotice = if (bn) "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।" else "Password must be at least 6 characters."
                            return@Button
                        }
                        formNotice = null
                        onSaveRememberedPhone(rememberChecked, cleanPhone)
                        onQueuePendingPhone(cleanPhone)
                        onGoogleSignIn()
                    },
                    enabled = !authLoading && isOnline,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChatboxPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("phone_login_submit_button")
                ) {
                    if (authLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (bn) "সাইন ইন হচ্ছে…" else "Signing in…")
                    } else {
                        Text(text = if (bn) "লগ ইন" else "Log in")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "Info",
                            tint = ChatboxInkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (bn) "তথ্য" else "Info",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChatboxInkMuted
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = ChatboxBorder)
                    Text(
                        text = if (bn) "অথবা" else "OR",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChatboxInkSubtle
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = ChatboxBorder)
                }

                // Primary Google Credential Manager Sign-In CTA
                Button(
                    onClick = {
                        onQueuePendingPhone(phoneInput)
                        onGoogleSignIn()
                    },
                    enabled = !authLoading && isOnline,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChatboxAccentViolet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (authLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (bn) "সাইন ইন হচ্ছে…" else "Signing in…")
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    color = ChatboxPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (bn) "Google দিয়ে চালিয়ে যান" else stringResource(R.string.continue_with_google),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Button(
                    onClick = {
                        onQueuePendingPhone(phoneInput)
                        onChooseGoogleAccount()
                    },
                    enabled = !authLoading && isOnline,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ChatboxBorderLavender),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChatboxSoftLavenderStrong,
                        contentColor = ChatboxPrimaryDark,
                        disabledContainerColor = ChatboxSoftLavender.copy(alpha = 0.6f),
                        disabledContentColor = ChatboxPrimaryDark.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_account_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (bn) "নতুন অ্যাকাউন্ট তৈরি করুন" else "Create account",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            bn = bn,
            selectedCode = selectedLang.code,
            onSelect = { code ->
                onLanguageSelected(code)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showInfoDialog) {
        Dialog(onDismissRequest = { showInfoDialog = false }) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (bn) "সাইন-ইন তথ্য" else "Sign-in information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (bn) {
                            "Chatbox-এ Google দিয়ে সাইন ইন করলে আপনার ৬-ডিজিটের Chatbox ID তৈরি হয়, যা দিয়ে বন্ধুদের সাথে রিয়েল-টাইম Live chats, Saira AI এবং Admin Panel ব্যবহার করতে পারবেন।"
                        } else {
                            "Signing in with Google creates your 6-digit Chatbox ID for real-time Firestore chats, compressed photo sharing, voice notes, Saira AI, and Admin controls."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChatboxInkMuted
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { showInfoDialog = false }) {
                            Text(if (bn) "বন্ধ করুন" else "Close")
                        }
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            bn = bn,
            onDismiss = { showForgotDialog = false },
            onSendPasswordReset = onSendPasswordReset
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    bn: Boolean,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) WorldLanguages.all
        else WorldLanguages.all.filter {
            it.label.lowercase().contains(q) ||
                it.region.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (bn) "সব দেশের ভাষা নির্বাচন করুন" else "Choose language",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                ChatboxSearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Search country or language…",
                    testTag = "language_search_input"
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { it.code }) { item ->
                        val isSelected = item.code == selectedCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(item.code) }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ChatboxAccentViolet else Color(0xFF2A2340)
                                )
                                Text(
                                    text = "${item.region} · ${item.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChatboxInkSubtle
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = ChatboxAccentViolet
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    bn: Boolean,
    onDismiss: () -> Unit,
    onSendPasswordReset: suspend (String) -> Result<Unit>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sentTo by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Mail, contentDescription = null, tint = ChatboxAccentViolet)
                    Text(
                        text = if (bn) "পাসওয়ার্ড রিসেট করুন" else "Reset password",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = if (bn) {
                        "আপনার Gmail বা অ্যাকাউন্ট ইমেল ঠিকানা দিন। Chatbox থেকে পাসওয়ার্ড রিসেট লিংক পাঠানো হবে।"
                    } else {
                        "Enter your Gmail address to receive a Chatbox password reset link."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ChatboxInkMuted
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        error = null
                        sentTo = null
                    },
                    enabled = !busy,
                    singleLine = true,
                    placeholder = { Text("name@gmail.com") },
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ChatboxErrorInk)
                }

                sentTo?.let { addr ->
                    Surface(
                        color = ChatboxSoftLavender,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (bn) "$addr ঠিকানায় রিসেট লিংক পাঠানো হয়েছে।" else "Reset link sent to $addr.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/"))
                                    )
                                }
                            }) {
                                Text(if (bn) "Gmail খুলুন" else "Open Gmail")
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (bn) "বাতিল" else "Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !busy && email.isNotBlank(),
                        onClick = {
                            val trimmed = email.trim()
                            if (!trimmed.contains("@") || !trimmed.contains(".")) {
                                error = if (bn) "সঠিক ইমেল ঠিকানা দিন।" else "Enter a valid email address."
                                return@Button
                            }
                            scope.launch {
                                busy = true
                                error = null
                                onSendPasswordReset(trimmed)
                                    .onSuccess { sentTo = trimmed }
                                    .onFailure { err -> error = err.message ?: "Could not send reset link." }
                                busy = false
                            }
                        }
                    ) {
                        Text(if (busy) "..." else if (bn) "পাঠান" else "Send link")
                    }
                }
            }
        }
    }
}
