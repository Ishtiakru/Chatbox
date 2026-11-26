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
import com.example.data.repository.AuthResult
import com.example.data.repository.PhoneCodeResult
import com.google.firebase.auth.PhoneAuthProvider
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.LoginBg
import com.example.ui.theme.LoginBrandBlue
import com.example.ui.theme.LoginFieldBorder
import com.example.ui.theme.LoginInkDark
import com.example.ui.theme.LoginInkMuted
import com.example.ui.theme.LoginLogoRing
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

@Composable
fun AuthLoginScreen(
    selectedLanguageCode: String,
    initialIdentifier: String,
    onLanguageSelected: (String) -> Unit,
    onBackSkipLogin: (() -> Unit)?,
    onLoginWithCredentials: suspend (identifier: String, password: String) -> Result<Unit>,
    onCreateAccount: suspend (fullName: String, identifier: String, password: String) -> Result<Unit>,
    onResetPassword: suspend (identifier: String, newPassword: String) -> Result<String>,
    onGoogleSignIn: suspend (Context) -> AuthResult,
    onSendPhoneCode: (suspend (Context, String, String, PhoneAuthProvider.ForceResendingToken?) -> PhoneCodeResult)? = null,
    onVerifyPhoneCode: (suspend (verificationId: String, code: String, name: String) -> Result<Unit>)? = null,
    onCancelPhoneFlow: () -> Unit = {}
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
    // SMS-code step that proves the number belongs to the user before a password
    // is created/changed. Holds the action to run once the code is verified.
    var pendingVerify by remember { mutableStateOf<PendingPhoneVerify?>(null) }
    val otpAvailable = onSendPhoneCode != null && onVerifyPhoneCode != null

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

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            containerColor = Color.White,
            title = { Text("Forgot password?", color = LoginInkDark) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("forgot_password_dialog")
                ) {
                    Text(
                        text = "আপনার মোবাইল নম্বর আর নতুন পাসওয়ার্ড দিন। নম্বরটি আপনার কিনা যাচাই করতে SMS-এ একটা কোড যাবে।",
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
                }
            },
            confirmButton = {
                Button(
                    enabled = !resetBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = LoginBrandBlue),
                    onClick = {
                        val resetId = resetIdentifier.trim()
                        if (otpAvailable && isPhoneLike(resetId)) {
                            if (newPassword.length < 6) {
                                resetError = "নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের দিন।"
                                return@Button
                            }
                            val chosen = newPassword
                            identifier = resetId
                            showForgotDialog = false
                            pendingVerify = PendingPhoneVerify(resetId, "") {
                                onResetPassword(resetId, chosen)
                            }
                            return@Button
                        }
                        scope.launch {
                            resetBusy = true
                            val res = onResetPassword(resetIdentifier, newPassword)
                            resetBusy = false
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
                    },
                    modifier = Modifier.testTag("forgot_reset_confirm_button")
                ) {
                    Text("Reset password")
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
                        text = "নাম, মোবাইল নম্বর আর একটা পাসওয়ার্ড দিন। নম্বরটি আপনার কিনা যাচাই করতে SMS-এ একটা কোড যাবে। পরে শুধু এই নম্বর আর পাসওয়ার্ড দিয়েই Log in করা যাবে।",
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
                    OutlinedButton(
                        enabled = !createBusy,
                        onClick = {
                            scope.launch {
                                createBusy = true
                                val res = onGoogleSignIn(context)
                                createBusy = false
                                when (res) {
                                    is AuthResult.Success -> showCreateAccountDialog = false
                                    is AuthResult.Cancelled -> Unit
                                    is AuthResult.Failure -> createError = res.message
                                }
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_account_google_button")
                    ) {
                        Text("Continue with Google")
                    }
                    createError?.let { NoticeBanner(text = it, isError = true) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !createBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = LoginBrandBlue),
                    onClick = {
                        val newId = newIdentifier.trim()
                        if (otpAvailable && isPhoneLike(newId)) {
                            when {
                                fullName.isBlank() -> createError = "আপনার নাম লিখুন।"
                                newPassword.length < 6 -> createError = "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের দিন।"
                                else -> {
                                    val name = fullName.trim()
                                    val chosen = newPassword
                                    identifier = newId
                                    showCreateAccountDialog = false
                                    pendingVerify = PendingPhoneVerify(newId, name) {
                                        onCreateAccount(name, newId, chosen).map { "Account তৈরি হয়েছে।" }
                                    }
                                }
                            }
                            return@Button
                        }
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

    val verify = pendingVerify
    if (verify != null && onSendPhoneCode != null && onVerifyPhoneCode != null) {
        PhoneOtpDialog(
            phone = verify.phone,
            name = verify.name,
            onSendCode = onSendPhoneCode,
            onVerifyCode = onVerifyPhoneCode,
            onVerified = verify.onVerified,
            onDone = { message ->
                pendingVerify = null
                password = ""
                infoMessage = message
            },
            onDismiss = {
                pendingVerify = null
                onCancelPhoneFlow()
            }
        )
    }
}

/** A create-account / forgot-password action waiting for the SMS-code check. */
private class PendingPhoneVerify(
    val phone: String,
    val name: String,
    val onVerified: suspend () -> Result<String>
)

private fun isPhoneLike(id: String): Boolean = !id.contains("@") && id.count { it.isDigit() } >= 6

/**
 * Verifies that the number belongs to the user (SMS code), then runs [onVerified]
 * (create the password / set a new password). Cancelling signs the half-finished
 * session out, so nobody gets in without a password.
 */
@Composable
private fun PhoneOtpDialog(
    phone: String,
    name: String,
    onSendCode: suspend (Context, String, String, PhoneAuthProvider.ForceResendingToken?) -> PhoneCodeResult,
    onVerifyCode: suspend (String, String, String) -> Result<Unit>,
    onVerified: suspend () -> Result<String>,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    suspend fun finish() {
        onVerified().onSuccess { msg -> onDone(msg) }.onFailure { e ->
            error = e.message ?: "আবার চেষ্টা করুন।"
        }
    }

    fun send(resend: Boolean) {
        if (busy) return
        error = null
        info = null
        scope.launch {
            busy = true
            when (val r = onSendCode(context, phone, name, if (resend) resendToken else null)) {
                is PhoneCodeResult.CodeSent -> {
                    verificationId = r.verificationId
                    resendToken = r.resendToken
                    info = "$phone নম্বরে SMS-এ ৬ সংখ্যার কোড পাঠানো হয়েছে।"
                }
                is PhoneCodeResult.SignedIn -> finish()
                is PhoneCodeResult.Failure -> error = r.message
            }
            busy = false
        }
    }

    fun verify() {
        val id = verificationId ?: return
        if (busy) return
        error = null
        scope.launch {
            busy = true
            onVerifyCode(id, code, name)
                .onSuccess { finish() }
                .onFailure { e -> error = e.message ?: "কোড মেলেনি। আবার চেষ্টা করুন।" }
            busy = false
        }
    }

    LaunchedEffect(phone) { send(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("phone_otp_dialog"),
        title = { Text("নম্বর যাচাই") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("৬ সংখ্যার কোড") },
                    singleLine = true,
                    enabled = verificationId != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { verify() }),
                    modifier = Modifier.fillMaxWidth().testTag("phone_otp_code_input")
                )
                TextButton(
                    onClick = { send(true) },
                    enabled = !busy,
                    modifier = Modifier.testTag("phone_otp_resend_button")
                ) { Text("কোড আসেনি? আবার পাঠান") }
                info?.let { NoticeBanner(text = it) }
                error?.let { NoticeBanner(text = it, isError = true) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && verificationId != null && code.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = LoginBrandBlue),
                onClick = { verify() },
                modifier = Modifier.testTag("phone_otp_submit_button")
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("যাচাই করুন")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("বাতিল") }
        }
    )
}
