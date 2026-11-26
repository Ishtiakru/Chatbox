package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.NoticeBanner
import com.example.ui.theme.LoginBrandBlue
import com.example.ui.theme.LoginInkDark
import com.example.ui.theme.LoginInkMuted
import kotlinx.coroutines.launch

/**
 * Shown on the home screen right after login while the account has no Gmail linked.
 * It cannot be dismissed (no back, no outside tap), so no menu or feature is usable
 * until a Gmail is chosen. It opens the phone's Gmail list automatically.
 */
@Composable
fun GmailRequiredDialog(
    needsPassword: Boolean,
    onLinkGmail: suspend (activityContext: Context, password: String?) -> Result<String>,
    /** Fallback when the device cannot open Google's Gmail list: save a typed Gmail. */
    onSaveTypedGmail: ((gmail: String, password: String?) -> Result<String>)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var failures by remember { mutableStateOf(0) }
    var typedGmail by remember { mutableStateOf("") }

    fun pick() {
        if (busy) return
        error = null
        scope.launch {
            busy = true
            onLinkGmail(context, if (needsPassword) password else null)
                .onFailure { e ->
                    error = e.message ?: "আবার চেষ্টা করুন।"
                    if (!(e.message ?: "").startsWith("একটা Gmail বেছে নিন")) failures++
                }
            busy = false
        }
    }

    // Open the Gmail list right away (old accounts first type a password).
    LaunchedEffect(Unit) {
        // Let the home screen finish appearing first; Google's sheet can fail if
        // it is opened while the activity is still starting.
        kotlinx.coroutines.delay(800)
        if (!needsPassword) pick()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("gmail_required_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📧", fontSize = 36.sp)
                Text(
                    text = "আপনার Gmail যুক্ত করুন",
                    color = LoginInkDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Chatbox চালাতে একটা Gmail দরকার। ফোনের Gmail-গুলো থেকে একটা বেছে নিন। পাসওয়ার্ড ভুলে গেলে এই Gmail দিয়েই নতুন পাসওয়ার্ড দিতে পারবেন।",
                    color = LoginInkMuted,
                    fontSize = 14.sp
                )
                if (needsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(64); error = null },
                        label = { Text("এই নম্বরের জন্য পাসওয়ার্ড দিন (কমপক্ষে ৬ অক্ষর)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gmail_required_password_input")
                    )
                }
                error?.let { NoticeBanner(text = it, isError = true) }
                Button(
                    onClick = { pick() },
                    enabled = !busy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = LoginBrandBlue, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gmail_required_pick_button")
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Gmail বেছে নিন", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (failures > 0 && onSaveTypedGmail != null) {
                    Text(
                        text = "Gmail-এর তালিকা না খুললে এখানে আপনার Gmail লিখুন:",
                        color = LoginInkMuted,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = typedGmail,
                        onValueChange = { typedGmail = it.trim().take(80); error = null },
                        label = { Text("আপনার Gmail (name@gmail.com)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gmail_manual_input")
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onSaveTypedGmail(typedGmail, if (needsPassword) password else null)
                                .onFailure { e -> error = e.message ?: "সঠিক Gmail লিখুন।" }
                        },
                        enabled = !busy && typedGmail.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("gmail_manual_save_button")
                    ) {
                        Text("এই Gmail সেভ করুন", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
