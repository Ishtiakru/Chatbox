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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ChatboxLoginBlue
import com.example.ui.theme.ChatboxLoginCanvasBottom
import com.example.ui.theme.ChatboxLoginCanvasTop
import com.example.ui.theme.ChatboxLoginCardBorder
import com.example.ui.theme.ChatboxLoginTextPrimary
import com.example.ui.theme.ChatboxLoginTextSecondary

@Composable
fun AuthLoginScreen(
    language: String,
    authReady: Boolean,
    authLoading: Boolean,
    onLanguageSelected: (String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    val bn = language == "bn"
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var showSignInInfoDialog by remember { mutableStateOf(false) }

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
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { languageMenuExpanded = true }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("language_selector"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (bn) "বাংলা (BD)" else "English (US)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChatboxLoginTextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(id = R.string.cd_language_chevron),
                            tint = ChatboxLoginTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("English (US)") },
                            onClick = {
                                languageMenuExpanded = false
                                onLanguageSelected("en")
                            },
                            modifier = Modifier.testTag("language_option_en")
                        )
                        DropdownMenuItem(
                            text = { Text("বাংলা (BD)") },
                            onClick = {
                                languageMenuExpanded = false
                                onLanguageSelected("bn")
                            },
                            modifier = Modifier.testTag("language_option_bn")
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(0.62f, fill = true)
                    .heightIn(min = 28.dp)
            )

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.2.dp, ChatboxLoginCardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chatbox_logo),
                    contentDescription = stringResource(id = R.string.cd_chatbox_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(
                modifier = Modifier
                    .weight(0.55f, fill = true)
                    .heightIn(min = 28.dp)
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
                        containerColor = ChatboxLoginBlue,
                        contentColor = Color.White,
                        disabledContainerColor = ChatboxLoginBlue.copy(alpha = 0.65f),
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
                            text = if (bn) "লগ ইন" else "Log in",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showSignInInfoDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("login_help_button")
                ) {
                    Text(
                        text = if (bn) "লগ ইনে সাহায্য প্রয়োজন?" else "Having trouble logging in?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ChatboxLoginTextPrimary
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .heightIn(min = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = stringResource(id = R.string.cd_brand_mark),
                        tint = ChatboxLoginBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chatbox",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ChatboxLoginTextPrimary
                    )
                }
            }
        }
    }

    if (showSignInInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSignInInfoDialog = false },
            title = {
                Text(
                    text = if (bn) "Google দিয়ে Chatbox লগ ইন" else "Sign in to Chatbox",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (bn) {
                        "Chatbox নিরাপদ Google সাইন-ইন ব্যবহার করে। প্রথমবার লগ ইন করলে আপনার অ্যাকাউন্ট স্বয়ংক্রিয়ভাবে তৈরি হবে, আলাদা পাসওয়ার্ড মনে রাখার প্রয়োজন নেই।"
                    } else {
                        "Chatbox uses your Google account via Android Credential Manager so there is no separate password to remember. Signing in automatically creates or restores your live Chatbox profile."
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
