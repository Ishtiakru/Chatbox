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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import com.example.R
import com.example.data.auth.openGmailInbox
import com.example.data.auth.sendPasswordResetToGmail
import com.example.data.auth.startPhoneNumberVerification
import com.example.data.auth.validatePasswordStrength
import com.example.data.auth.validatePhoneNumber
import com.example.data.auth.verifyPhoneOtpCode
import com.example.data.model.WorldLanguages
import com.example.ui.components.ChatboxNoticeBanner
import com.example.ui.theme.ChatboxLavenderLogo
import com.example.ui.theme.ChatboxLoginCanvasBottom
import com.example.ui.theme.ChatboxLoginCanvasTop
import com.example.ui.theme.ChatboxLoginCardBorder
import com.example.ui.theme.ChatboxLoginPrimary
import com.example.ui.theme.ChatboxLoginTextPrimary
import com.example.ui.theme.ChatboxLoginTextSecondary
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch

@Composable
fun AuthLoginScreen(
    language: String,
    authReady: Boolean,
    authLoading: Boolean,
    isOnline: Boolean = true,
    rememberMeEnabled: Boolean = false,
    rememberedPhone: String = "",
    onSaveRememberedPhone: (Boolean, String) -> Unit = { _, _ -> },
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

    var phoneInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmPasswordInput by rememberSaveable { mutableStateOf("") }
    var rememberMeChecked by rememberSaveable { mutableStateOf(rememberMeEnabled) }
    var hasEditedPhone by rememberSaveable { mutableStateOf(false) }
    var hasToggledRememberMe by rememberSaveable { mutableStateOf(false) }
    var isCreateAccountMode by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var phoneFieldError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordFieldError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmPasswordError by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneAuthBusy by remember { mutableStateOf(false) }
    var phoneAuthError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(rememberMeEnabled) {
        if (!hasToggledRememberMe) {
            rememberMeChecked = rememberMeEnabled
        }
    }

    LaunchedEffect(rememberedPhone) {
        if (!hasEditedPhone && rememberedPhone.isNotBlank() && phoneInput.isEmpty()) {
            phoneInput = rememberedPhone
        }
    }

    var activeVerificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var verifiedPhoneDisplay by rememberSaveable { mutableStateOf("") }

    fun submitNumberAndPasswordLogin(isCreateAccount: Boolean) {
        if (!authReady || authLoading || phoneAuthBusy) return
        if (!isOnline) {
            phoneAuthError = if (bn) {
                "লগ ইন করতে ইন্টারনেট সংযোগ প্রয়োজন।"
            } else {
                "An internet connection is required to sign in."
            }
            return
        }

        val phoneCheck = validatePhoneNumber(phoneInput, isBengali = bn)
        val passCheck = validatePasswordStrength(passwordInput, isBengali = bn)
        val confirmCheck = if (isCreateAccount && confirmPasswordInput != passwordInput) {
            if (bn) "পাসওয়ার্ড দুটি মিলছে না।" else "Passwords do not match."
        } else {
            null
        }

        phoneFieldError = phoneCheck.exceptionOrNull()?.message
        passwordFieldError = passCheck
        confirmPasswordError = confirmCheck

        val normalizedPhone = phoneCheck.getOrNull()
        if (normalizedPhone == null || passCheck != null || confirmCheck != null) {
            return
        }

        phoneAuthError = null
        onSaveRememberedPhone(rememberMeChecked, phoneInput.trim())
        phoneAuthBusy = true
        verifiedPhoneDisplay = normalizedPhone
        startPhoneNumberVerification(
            context = context,
            rawPhone = normalizedPhone,
            scope = scope,
            resendToken = null,
            onCodeSent = { verId, token ->
                phoneAuthBusy = false
                activeVerificationId = verId
                resendToken = token
            },
            onAutoVerified = {
                phoneAuthBusy = false
                activeVerificationId = null
            },
            onError = { errMsg ->
                phoneAuthBusy = false
                phoneAuthError = if (isCreateAccount && errMsg.contains("Phone sign-in is not enabled", ignoreCase = true)) {
                    if (bn) {
                        "মোবাইল নম্বর ভেরিফিকেশন চালু করতে Firebase Console → Authentication → Sign-in method → Phone অন করুন, অথবা নিচে Google দিয়ে নতুন অ্যাকাউন্ট তৈরি করুন।"
                    } else {
                        errMsg
                    }
                } else {
                    errMsg
                }
            }
        )
    }

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
                    .weight(0.38f, fill = true)
                    .heightIn(min = 18.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
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
                    .weight(0.32f, fill = true)
                    .heightIn(min = 18.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isOnline) {
                    ChatboxNoticeBanner(
                        text = if (bn) {
                            "ডাটা বা ইন্টারনেট সংযোগ নেই। প্রথমবার লগ ইন করতে ইন্টারনেট প্রয়োজন; আগে লগ ইন করা থাকলে অফলাইনেই অ্যাপসে ঢুকে শুধু এসএমএস পাঠাতে পারবেন।"
                        } else {
                            "No internet connection. Data connection is required to log in. Signed-in users can enter offline for text SMS only."
                        },
                        isError = true,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("offline_login_banner")
                    )
                }

                if (!phoneAuthError.isNullOrBlank()) {
                    ChatboxNoticeBanner(
                        text = phoneAuthError.orEmpty(),
                        isError = true,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .testTag("phone_login_error_banner")
                    )
                }

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        hasEditedPhone = true
                        val next = it.take(24)
                        phoneInput = next
                        phoneFieldError = null
                        phoneAuthError = null
                        if (rememberMeChecked) {
                            onSaveRememberedPhone(true, next)
                        }
                    },
                    enabled = authReady && !authLoading && !phoneAuthBusy && isOnline,
                    isError = phoneFieldError != null,
                    supportingText = phoneFieldError?.let { err -> { Text(err) } },
                    singleLine = true,
                    label = { Text(if (bn) "মোবাইল নম্বর" else "Phone number") },
                    placeholder = { Text(if (bn) "01XXXXXXXXX বা +880..." else "+8801XXXXXXXXX") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = null,
                            tint = ChatboxLoginTextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.7f),
                        focusedBorderColor = ChatboxLavenderLogo,
                        unfocusedBorderColor = ChatboxLoginCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_phone_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it.take(64)
                        passwordFieldError = null
                        confirmPasswordError = null
                        phoneAuthError = null
                    },
                    enabled = authReady && !authLoading && !phoneAuthBusy && isOnline,
                    isError = passwordFieldError != null,
                    supportingText = passwordFieldError?.let { err -> { Text(err) } },
                    singleLine = true,
                    label = { Text(if (bn) "পাসওয়ার্ড" else "Password") },
                    placeholder = { Text(if (bn) "পাসওয়ার্ড লিখুন" else "Enter password") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = ChatboxLoginTextSecondary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("login_password_visibility_button")
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    id = if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password
                                ),
                                tint = ChatboxLoginTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isCreateAccountMode) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isCreateAccountMode) submitNumberAndPasswordLogin(isCreateAccount = false)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.7f),
                        focusedBorderColor = ChatboxLavenderLogo,
                        unfocusedBorderColor = ChatboxLoginCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                if (!isCreateAccountMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = true },
                            enabled = isOnline,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .minimumInteractiveComponentSize()
                                .testTag("forgot_password_button")
                        ) {
                            Text(
                                text = if (bn) "পাসওয়ার্ড ভুলে গেছেন?" else "Forgot password?",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (isOnline) ChatboxLoginPrimary else ChatboxLoginTextSecondary
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isCreateAccountMode) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirmPasswordInput,
                            onValueChange = {
                                confirmPasswordInput = it.take(64)
                                confirmPasswordError = null
                                phoneAuthError = null
                            },
                            enabled = authReady && !authLoading && !phoneAuthBusy && isOnline,
                            isError = confirmPasswordError != null,
                            supportingText = confirmPasswordError?.let { err -> { Text(err) } },
                            singleLine = true,
                            label = { Text(if (bn) "পাসওয়ার্ড নিশ্চিত করুন" else "Confirm password") },
                            placeholder = { Text(if (bn) "আবার পাসওয়ার্ড লিখুন" else "Re-enter password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = ChatboxLoginTextSecondary
                                )
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitNumberAndPasswordLogin(isCreateAccount = true) }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.7f),
                                focusedBorderColor = ChatboxLavenderLogo,
                                unfocusedBorderColor = ChatboxLoginCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_confirm_password_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Checkbox,
                            enabled = authReady && !authLoading && !phoneAuthBusy
                        ) {
                            val nextChecked = !rememberMeChecked
                            hasToggledRememberMe = true
                            rememberMeChecked = nextChecked
                            onSaveRememberedPhone(nextChecked, phoneInput)
                        }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("remember_me_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberMeChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = ChatboxLavenderLogo,
                            uncheckedColor = ChatboxLoginTextSecondary
                        ),
                        modifier = Modifier.testTag("remember_me_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bn) "আমার নম্বর মনে রাখুন" else "Remember me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChatboxLoginTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { submitNumberAndPasswordLogin(isCreateAccount = isCreateAccountMode) },
                    enabled = authReady && !authLoading && !phoneAuthBusy && isOnline,
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
                        .testTag("number_password_login_button")
                ) {
                    if (phoneAuthBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (bn) "ভেরিফাই হচ্ছে…" else "Verifying…",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    } else {
                        Text(
                            text = when {
                                isCreateAccountMode && bn -> "মোবাইল দিয়ে অ্যাকাউন্ট তৈরি"
                                isCreateAccountMode -> "Verify & Create Account"
                                bn -> "লগ ইন করুন"
                                else -> "Log in"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                if (isCreateAccountMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            isCreateAccountMode = false
                            confirmPasswordError = null
                            phoneAuthError = null
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("back_to_login_button")
                    ) {
                        Text(
                            text = if (bn) "ইতিমধ্যে অ্যাকাউন্ট আছে? লগ ইন করুন" else "Already have an account? Log in",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = ChatboxLoginTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = ChatboxLoginCardBorder
                    )
                    Text(
                        text = if (bn) "অথবা" else "OR",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChatboxLoginTextSecondary
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = ChatboxLoginCardBorder
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = { onGoogleSignIn() },
                    enabled = authReady && !authLoading && !phoneAuthBusy && isOnline,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ChatboxLoginCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!authReady || authLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ChatboxLavenderLogo
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = when {
                                !authReady -> if (bn) "অ্যাকাউন্ট যাচাই হচ্ছে…" else "Checking account…"
                                authLoading -> if (bn) "Google দিয়ে সাইন ইন হচ্ছে…" else "Signing in with Google…"
                                else -> if (bn) "Google দিয়ে সাইন ইন করুন" else "Sign in with Google"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ChatboxLoginTextPrimary,
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

                if (!isCreateAccountMode) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            isCreateAccountMode = true
                            phoneFieldError = null
                            passwordFieldError = null
                            confirmPasswordError = null
                            phoneAuthError = null
                        },
                        enabled = authReady && !authLoading && !phoneAuthBusy && isOnline,
                        shape = CircleShape,
                        border = BorderStroke(
                            1.2.dp,
                            if (isOnline) ChatboxLoginPrimary else ChatboxLoginPrimary.copy(alpha = 0.45f)
                        ),
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
            }

            Spacer(
                modifier = Modifier
                    .weight(0.8f, fill = true)
                    .heightIn(min = 20.dp)
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
            isOnline = isOnline,
            onDismiss = { showForgotPasswordDialog = false },
            onSendReset = { email -> sendPasswordResetToGmail(email, isBengali = bn) },
            onOpenGmail = { openGmailInbox(context) },
            scope = scope
        )
    }

    activeVerificationId?.let { verificationId ->
        PhoneOtpVerificationDialog(
            phoneDisplay = verifiedPhoneDisplay,
            isBengali = bn,
            onDismiss = { activeVerificationId = null },
            onVerifyOtp = { otpCode -> verifyPhoneOtpCode(verificationId, otpCode) },
            onResendOtp = {
                startPhoneNumberVerification(
                    context = context,
                    rawPhone = verifiedPhoneDisplay,
                    scope = scope,
                    resendToken = resendToken,
                    onCodeSent = { newId, newToken ->
                        activeVerificationId = newId
                        resendToken = newToken
                    },
                    onAutoVerified = {
                        activeVerificationId = null
                    },
                    onError = { errMsg ->
                        phoneAuthError = errMsg
                        activeVerificationId = null
                    }
                )
            },
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
                        "মোবাইল নম্বর ও পাসওয়ার্ড দিয়ে লগ ইন করলে আপনার নম্বরে একটি ভেরিফিকেশন কোড পাঠানো হয় (Firebase Phone Auth চালু থাকলে), অথবা নিচে Google দিয়ে এক ট্যাপে নিরাপদভাবে লগ ইন করতে পারবেন।"
                    } else {
                        "Logging in with mobile number and password uses Firebase Phone verification when enabled in Firebase Console, or you can sign in immediately using Google Sign-In below."
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
private fun PhoneOtpVerificationDialog(
    phoneDisplay: String,
    isBengali: Boolean,
    onDismiss: () -> Unit,
    onVerifyOtp: suspend (String) -> Result<Unit>,
    onResendOtp: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var otpCode by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submitCode() {
        if (busy) return
        val clean = otpCode.trim()
        if (clean.length < 4) {
            errorMessage = if (isBengali) "সঠিক ভেরিফিকেশন কোড দিন।" else "Enter the verification code."
            return
        }
        busy = true
        errorMessage = null
        scope.launch {
            val result = onVerifyOtp(clean)
            busy = false
            result.onSuccess {
                onDismiss()
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Invalid verification code."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Sms,
                contentDescription = null,
                tint = ChatboxLavenderLogo
            )
        },
        title = {
            Text(
                text = if (isBengali) "ফোন ভেরিফিকেশন কোড" else "Verify mobile number",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isBengali) {
                        "$phoneDisplay নম্বরে পাঠানো ৬ সংখ্যার কোডটি লিখুন।"
                    } else {
                        "Enter the 6-digit SMS code sent to $phoneDisplay."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = {
                        otpCode = it.filter { ch -> ch.isDigit() }.take(8)
                        errorMessage = null
                    },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(if (isBengali) "ভেরিফিকেশন কোড" else "SMS Code") },
                    placeholder = { Text("123456") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitCode() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_otp_input")
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(
                    onClick = onResendOtp,
                    enabled = !busy,
                    modifier = Modifier.testTag("resend_otp_button")
                ) {
                    Text(if (isBengali) "কোড আবার পাঠান" else "Resend code")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitCode() },
                enabled = !busy && otpCode.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ChatboxLavenderLogo),
                modifier = Modifier.testTag("verify_otp_confirm_button")
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isBengali) "নিশ্চিত করুন" else "Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBengali) "বাতিল" else "Cancel")
            }
        }
    )
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
    isOnline: Boolean,
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
        val result = validateResetRequestInput(
            rawEmail = gmailInput,
            isOnline = isOnline,
            isBengali = isBengali
        )
        if (result.errorMessage != null) {
            errorMessage = result.errorMessage
            return
        }
        val trimmed = result.normalizedEmail ?: return
        busy = true
        errorMessage = null
        scope.launch {
            val sendResult = onSendReset(trimmed)
            busy = false
            sendResult.onSuccess {
                sentToEmail = trimmed
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: if (isBengali) {
                    "পাসওয়ার্ড রিসেট লিংক পাঠানো যায়নি।"
                } else {
                    "Could not send password reset link."
                }
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
                text = if (isBengali) "পাসওয়ার্ড রিসেট করুন" else "Reset password",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            ForgotPasswordResetFields(
                gmailInput = gmailInput,
                busy = busy,
                errorMessage = errorMessage,
                sentToEmail = sentToEmail,
                isBengali = isBengali,
                onGmailChange = {
                    gmailInput = it
                    errorMessage = null
                    sentToEmail = null
                },
                onSubmit = { submitReset() },
                onOpenGmail = {
                    if (!onOpenGmail()) {
                        errorMessage = if (isBengali) {
                            "এই ডিভাইসে Gmail বা ইমেল অ্যাপ খোলা যায়নি।"
                        } else {
                            "Unable to open Gmail or an email app on this device."
                        }
                    }
                }
            )
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

internal data class ResetEmailValidationResult(
    val normalizedEmail: String? = null,
    val errorMessage: String? = null
)

internal fun validateResetRequestInput(
    rawEmail: String,
    isOnline: Boolean,
    isBengali: Boolean
): ResetEmailValidationResult {
    if (!isOnline) {
        return ResetEmailValidationResult(
            errorMessage = if (isBengali) {
                "পাসওয়ার্ড রিসেট ইমেল পাঠাতে ইন্টারনেট সংযোগ প্রয়োজন।"
            } else {
                "An internet connection is required to send a reset email."
            }
        )
    }
    val trimmed = rawEmail.trim()
    if (trimmed.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
        return ResetEmailValidationResult(
            errorMessage = if (isBengali) {
                "সঠিক ইমেল / Gmail ঠিকানা দিন।"
            } else {
                "Please enter a valid email address."
            }
        )
    }
    return ResetEmailValidationResult(normalizedEmail = trimmed)
}

@Composable
internal fun ForgotPasswordResetFields(
    gmailInput: String,
    busy: Boolean,
    errorMessage: String?,
    sentToEmail: String?,
    isBengali: Boolean,
    onGmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenGmail: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (isBengali) {
                "আপনার ফোনের Gmail বা অ্যাকাউন্ট ইমেল ঠিকানা দিন। Firebase-এর মাধ্যমে পাসওয়ার্ড রিসেট লিংক পাঠানো হবে।"
            } else {
                "Enter your account email or Gmail address below to receive a Firebase password reset link."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = gmailInput,
            onValueChange = onGmailChange,
            enabled = !busy,
            isError = !errorMessage.isNullOrBlank(),
            singleLine = true,
            label = { Text(if (isBengali) "আপনার ইমেল / Gmail" else "Email / Gmail address") },
            placeholder = { Text("name@gmail.com") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("forgot_password_email_input")
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("forgot_password_error_text")
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
                            "$sentToEmail ঠিকানায় পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে। Gmail ইনবক্স বা Spam ফোল্ডার চেক করুন।"
                        } else {
                            "Password reset link sent to $sentToEmail. Check your Gmail inbox or Spam folder."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = onOpenGmail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_gmail_button")
                    ) {
                        Text(if (isBengali) "Gmail খুলুন" else "Open Gmail")
                    }
                }
            }
        }
    }
}

