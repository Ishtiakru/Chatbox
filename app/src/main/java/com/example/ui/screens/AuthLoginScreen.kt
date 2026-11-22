package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ChatboxBodyInk
import com.example.ui.theme.ChatboxInk
import com.example.ui.theme.ChatboxLoginOverlayBottom
import com.example.ui.theme.ChatboxLoginOverlayTop
import com.example.ui.theme.ChatboxLoginPrimary

@Composable
fun AuthLoginScreen(
    language: String,
    authReady: Boolean,
    authLoading: Boolean,
    onLanguageSelected: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onExploreDemo: () -> Unit
) {
    val bn = language == "bn"
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.login_lavender),
            contentDescription = stringResource(id = R.string.cd_login_backdrop),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(ChatboxLoginOverlayTop, ChatboxLoginOverlayBottom)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (bn) "ভাষা" else "Language",
                        style = MaterialTheme.typography.labelLarge,
                        color = ChatboxBodyInk
                    )
                    Box {
                        Surface(
                            onClick = { languageMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("language_selector")
                        ) {
                            Text(
                                text = if (bn) "বাংলা (BD)" else "English (US)",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChatboxInk,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("বাংলা (BD)") },
                                onClick = {
                                    languageMenuExpanded = false
                                    onLanguageSelected("bn")
                                },
                                modifier = Modifier.testTag("language_option_bn")
                            )
                            DropdownMenuItem(
                                text = { Text("English (US)") },
                                onClick = {
                                    languageMenuExpanded = false
                                    onLanguageSelected("en")
                                },
                                modifier = Modifier.testTag("language_option_en")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chatbox_logo),
                    contentDescription = stringResource(id = R.string.cd_chatbox_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (bn) "Chatbox-এ স্বাগতম" else "Welcome to Chatbox",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChatboxInk,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (bn) "আপনার কথোপকথন, এক সুন্দর জায়গায়।" else "Your conversations, in one thoughtful space.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ChatboxBodyInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onGoogleSignIn,
                    enabled = authReady && !authLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChatboxLoginPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (!authReady) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (bn) "অ্যাকাউন্ট যাচাই হচ্ছে…" else "Checking account…")
                    } else if (authLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (bn) "সাইন ইন হচ্ছে…" else "Signing in…")
                    } else {
                        Text(
                            text = if (bn) "Google দিয়ে লগ ইন" else "Log in with Google",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (bn) "নিরাপদে সাইন ইন করতে আপনার Google অ্যাকাউন্ট ব্যবহার করুন।" else "Use your Google account to sign in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChatboxBodyInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedButton(
                    onClick = onExploreDemo,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ChatboxLoginPrimary.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.72f),
                        contentColor = ChatboxInk
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("explore_demo_button")
                ) {
                    Text(
                        text = if (bn) "ডেমো দেখুন" else "Explore the demo",
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (bn) "ডেমোতে সাইন ইন হয় না; লাইভ ফিচারের জন্য সাইন ইন প্রয়োজন।" else "Demo access does not sign you in; live features require an account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChatboxBodyInk.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            Text(
                text = "✦  Chatbox",
                style = MaterialTheme.typography.labelLarge,
                color = ChatboxInk.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }
    }
}
