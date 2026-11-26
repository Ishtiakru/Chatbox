package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.WorldLanguages
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.LoginBg
import com.example.ui.theme.LoginBrandBlue
import com.example.ui.theme.LoginFieldBorder
import com.example.ui.theme.LoginInkDark
import com.example.ui.theme.LoginInkMuted
import com.example.ui.theme.LoginLogoRing
import kotlinx.coroutines.launch

@Composable
fun AuthLoginScreen(
    selectedLanguageCode: String,
    initialIdentifier: String,
    onLanguageSelected: (String) -> Unit,
    onBackSkipLogin: (() -> Unit)?,
    onLoginWithCredentials: suspend (identifier: String, password: String) -> Result<Unit>,
    onCreateAccount: suspend (fullName: String, identifier: String, password: String) -> Result<Unit>,
    /** Forgot password: the number's new password is saved after the user picks the Gmail linked to it. */
    onResetPassword: suspend (activityContext: Context, identifier: String, newPassword: String) -> Result<String>,
    /** Forgot password when the Gmail is not on this phone: email a link to that Gmail. */
    onSendResetCode: (suspend (gmail: String) -> Result<String>)? = null,
    /** ...then, after the link was clicked, save the new password. */
    onResetWithCode: (suspend (identifier: String, gmail: String, newPassword: String) -> Result<String>)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var identifier by remember(initialIdentifier) { mutableStateOf(initialIdentifier) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showIdentifierHint by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }

    val languageLabel = remember(selectedLanguageCode) {
        WorldLanguages.displayLabel(selectedLanguageCode)
    }

    fun submitLogin() {
        if (isSubmitting) return
        errorMessage = null
        infoMessage = null
        scope.launch {
            isSubmitting = true
            val res = onLoginWithCredentials(identifier, password)
            isSubmitting = false
            res.onFailure { err ->
                errorMessage = err.message ?: "Unable to log in. Please try again."
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("auth_login_screen")
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top bar: back arrow on left (lets user skip login directly into Chatbox)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBackSkipLogin != null) IconButton(
                            onClick = onBackSkipLogin,
                            modifier = Modifier.testTag("login_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Continue to Chatbox",
                                tint = LoginInkDark
                            )
                        }
                    }

                    // Centered Language Selector ("English (US) v")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showLanguageDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("login_language_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = languageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LoginInkMuted,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Choose language",
                            tint = LoginInkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Centered Circular Logo + Form Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Centered Circular Logo
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.2.dp, LoginLogoRing),
                        modifier = Modifier.size(64.dp)
                    ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.chatbox_logo_lavender),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(34.dp))

            // Form Inputs + Login CTA + Forgot Password
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it.take(100)
                        errorMessage = null
                    },
                    placeholder = {
                        Text(
                            text = "Mobile number",
                            color = LoginInkMuted,
                            fontSize = 15.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { showIdentifierHint = !showIdentifierHint },
                            modifier = Modifier.testTag("login_identifier_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Input format info",
                                tint = LoginInkDark
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = LoginBrandBlue,
                        unfocusedBorderColor = LoginFieldBorder,
                        focusedTextColor = LoginInkDark,
                        unfocusedTextColor = LoginInkDark,
                        cursorColor = LoginBrandBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_identifier_input")
                )

                AnimatedVisibility(visible = showIdentifierHint) {
                    NoticeBanner(
                        text = "Account খোলার সময় যে মোবাইল নম্বর আর পাসওয়ার্ড দিয়েছিলেন, সেটা দিয়ে Log in করুন। নতুন হলে \"Create new account\" চাপুন।",
                        modifier = Modifier.testTag("login_identifier_hint_banner")
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it.take(64)
                        errorMessage = null
                    },
                    placeholder = {
                        Text(
                            text = "Password",
                            color = LoginInkMuted,
                            fontSize = 15.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                    trailingIcon = {
                        if (password.isNotEmpty()) {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("login_password_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = LoginInkMuted
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = LoginBrandBlue,
                        unfocusedBorderColor = LoginFieldBorder,
                        focusedTextColor = LoginInkDark,
                        unfocusedTextColor = LoginInkDark,
                        cursorColor = LoginBrandBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                errorMessage?.let { err ->
                    NoticeBanner(text = err, isError = true)
                }
                infoMessage?.let { msg ->
                    NoticeBanner(text = msg)
                }

                Spacer(Modifier.height(2.dp))

                Button(
                    onClick = { submitLogin() },
                    enabled = !isSubmitting,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginBrandBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_submit_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "Log in",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                TextButton(
                    onClick = { showForgotDialog = true },
                    modifier = Modifier.testTag("login_forgot_password_button")
                ) {
                    Text(
                        text = "Forgot password?",
                        color = LoginInkDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
                }

            // Bottom Outlined "Create new account" Pill Button + Footer Brand Mark
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(
                    onClick = { showCreateAccountDialog = true },
                    shape = CircleShape,
                    border = BorderStroke(1.2.dp, LoginBrandBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LoginBrandBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("login_create_account_button")
                ) {
                    Text(
                        text = "Create new account",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AllInclusive,
                        contentDescription = null,
                        tint = LoginInkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        color = LoginInkMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            }
        }
    }

    // Language Picker Dialog
    if (showLanguageDialog) {
        val languages = remember { WorldLanguages.all }
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_language_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select language",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LoginInkDark
                        )
                        IconButton(onClick = { showLanguageDialog = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(languages, key = { it.code }) { lang ->
                            val selected = lang.code.equals(selectedLanguageCode, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onLanguageSelected(lang.code)
                                        showLanguageDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = lang.label,
                                        color = if (selected) LoginBrandBlue else LoginInkDark,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        text = lang.region,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LoginInkMuted
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = LoginBrandBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Forgot Password Dialog
    if (showForgotDialog) {
        var resetIdentifier by remember(identifier) { mutableStateOf(identifier) }
        var newPassword by remember { mutableStateOf("") }
        var resetError by remember { mutableStateOf<String?>(null) }
        var resetBusy by remember { mutableStateOf(false) }
        var codeGmail by remember { mutableStateOf("") }
        var codeSent by remember { mutableStateOf(false) }
        var codeInfo by remember { mutableStateOf<String?>(null) }
        val useLink = onSendResetCode != null && onResetWithCode != null
        fun sendLink(gmail: String) {
            val send = onSendResetCode ?: return
            scope.launch {
                resetBusy = true
                resetError = null
                send(gmail)
                    .onSuccess { codeSent = true; codeInfo = it }
                    .onFailure { resetError = it.message ?: "আবার চেষ্টা করুন।" }
                resetBusy = false
            }
        }
        val openChooser: (() -> Unit)? = if (useLink) {
            rememberDeviceGmailChooser { result ->
                when (result) {
                    is DeviceGmailResult.Chosen -> {
                        codeGmail = result.gmail
                        codeSent = false
                        codeInfo = null
                        sendLink(result.gmail)
                    }
                    DeviceGmailResult.Cancelled -> resetError = "একটা Gmail বেছে নিন।"
                    DeviceGmailResult.Unavailable -> resetError = "ফোনের Gmail তালিকা খুলছে না। নিচে Gmail লিখে লিংক পাঠান।"
                }
            }
        } else null
        fun finishReset(res: Result<String>) {
            res.onSuccess { msg ->
                identifier = resetIdentifier.trim()
                password = ""
                infoMessage = msg
                errorMessage = null
                showForgotDialog = false
            }.onFailure { err ->
                resetError = err.message ?: "Unable to reset password."
            }
        }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            containerColor = Color.White,
            title = { Text("Forgot password?", color = LoginInkDark) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .testTag("forgot_password_dialog")
                ) {
                    Text(
                        text = if (useLink) {
                            "মোবাইল নম্বর আর নতুন পাসওয়ার্ড দিন। তারপর এই নম্বরে যুক্ত যেকোনো একটা Gmail বেছে নিন — সেই Gmail-এ একটা লিংক যাবে, লিংকে চাপলেই পাসওয়ার্ড বদলানো যাবে।"
                        } else {
                            "মোবাইল নম্বর আর নতুন পাসওয়ার্ড দিন। তারপর এই নম্বরে যুক্ত যেকোনো একটা Gmail দিয়ে যাচাই করুন — মিললে পাসওয়ার্ড বদলে যাবে।"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LoginInkMuted
                    )
                    OutlinedTextField(
                        value = resetIdentifier,
                        onValueChange = {
                            resetIdentifier = it.take(100)
                            resetError = null
                        },
                        label = { Text("Mobile number") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_identifier_input")
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it.take(64)
                            resetError = null
                        },
                        label = { Text("New password (কমপক্ষে ৬ অক্ষর)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_new_password_input")
                    )
                    resetError?.let { NoticeBanner(text = it, isError = true) }
                    if (onSendResetCode != null && onResetWithCode != null) {
                        Text(
                            text = "অথবা Gmail লিখুন (ফোনে না থাকলেও চলবে):",
                            style = MaterialTheme.typography.bodySmall,
                            color = LoginInkMuted
                        )
                        OutlinedTextField(
                            value = codeGmail,
                            onValueChange = {
                                codeGmail = it.trim().take(80)
                                codeSent = false
                                codeInfo = null
                                resetError = null
                            },
                            label = { Text("আপনার Gmail") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_code_gmail_input")
                        )
                        codeInfo?.let { NoticeBanner(text = it, isError = false) }
                        OutlinedButton(
                            enabled = !resetBusy && codeGmail.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    resetBusy = true
                                    resetError = null
                                    if (!codeSent) {
                                        onSendResetCode(codeGmail)
                                            .onSuccess { codeSent = true; codeInfo = it }
                                            .onFailure { resetError = it.message ?: "আবার চেষ্টা করুন।" }
                                    } else {
                                        finishReset(onResetWithCode(resetIdentifier, codeGmail, newPassword))
                                    }
                                    resetBusy = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_code_button")
                        ) {
                            Text(if (codeSent) "লিংকে চেপেছি — পাসওয়ার্ড বদলান" else "Gmail-এ লিংক পাঠান")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !resetBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = LoginBrandBlue),
                    onClick = {
                        scope.launch {
                            resetBusy = true
                            if (openChooser != null) {
                                resetBusy = false
                                if (codeSent && codeGmail.isNotBlank()) {
                                    resetBusy = true
                                    finishReset(onResetWithCode!!.invoke(resetIdentifier, codeGmail, newPassword))
                                    resetBusy = false
                                } else {
                                    openChooser()
                                }
                                return@launch
                            }
                            val res = onResetPassword(context, resetIdentifier, newPassword)
                            resetBusy = false
                            finishReset(res)
                        }
                    },
                    modifier = Modifier.testTag("forgot_reset_confirm_button")
                ) {
                    if (resetBusy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(if (openChooser != null && codeSent) "লিংকে চেপেছি — পাসওয়ার্ড বদলান" else "ফোনের Gmail বেছে নিন")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create New Account Dialog
    if (showCreateAccountDialog) {
        var fullName by remember { mutableStateOf("") }
        var newIdentifier by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var createError by remember { mutableStateOf<String?>(null) }
        var createBusy by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateAccountDialog = false },
            containerColor = Color.White,
            title = { Text("Create new account", color = LoginInkDark) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("create_account_dialog")
                ) {
                    Text(
                        text = "নাম, মোবাইল নম্বর আর একটা পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) দিন। পরে শুধু এই নম্বর আর এই পাসওয়ার্ড দিয়েই Log in করা যাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = LoginInkMuted
                    )
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it.take(60)
                            createError = null
                        },
                        label = { Text("Full name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_account_name_input")
                    )
                    OutlinedTextField(
                        value = newIdentifier,
                        onValueChange = {
                            newIdentifier = it.take(100)
                            createError = null
                        },
                        label = { Text("Mobile number") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_account_identifier_input")
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it.take(64)
                            createError = null
                        },
                        label = { Text("Password (কমপক্ষে ৬ অক্ষর)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_account_password_input")
                    )
                    createError?.let { NoticeBanner(text = it, isError = true) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !createBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = LoginBrandBlue),
                    onClick = {
                        scope.launch {
                            createBusy = true
                            val res = onCreateAccount(fullName, newIdentifier, newPassword)
                            createBusy = false
                            res.onSuccess {
                                showCreateAccountDialog = false
                            }.onFailure { err ->
                                createError = err.message ?: "Unable to create account."
                            }
                        }
                    },
                    modifier = Modifier.testTag("create_account_submit_button")
                ) {
                    Text("Create account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

}
