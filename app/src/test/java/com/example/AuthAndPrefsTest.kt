package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.normalizePhoneNumber
import com.example.data.auth.sendPasswordResetToGmail
import com.example.data.auth.validatePasswordStrength
import com.example.data.auth.validatePhoneNumber
import com.example.data.repository.AppPreferencesRepository
import com.example.ui.screens.AuthLoginScreen
import com.example.ui.screens.ForgotPasswordResetFields
import com.example.ui.screens.validateResetRequestInput
import com.example.ui.theme.ChatboxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp")
class AuthAndPrefsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `phone normalization and password validation handle valid and invalid inputs`() {
        assertEquals("+8801712345678", normalizePhoneNumber("01712345678"))
        assertEquals("+8801712345678", normalizePhoneNumber("01712-345-678"))
        assertEquals("+8801712345678", normalizePhoneNumber("8801712345678"))
        assertEquals("+14155552671", normalizePhoneNumber("+1 415 555 2671"))

        assertTrue(validatePhoneNumber("01712345678").isSuccess)
        assertTrue(validatePhoneNumber("1234").isFailure)

        assertNull(validatePasswordStrength("secret123"))
        assertNotNull(validatePasswordStrength(""))
        assertNotNull(validatePasswordStrength("12345"))
    }

    @Test
    fun `remember me persists and clears phone number in DataStore`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppPreferencesRepository(context)

        repo.saveRememberedLoginPhone(rememberMe = true, phone = "01711223344")
        assertTrue(repo.rememberMeEnabled.first())
        assertEquals("01711223344", repo.rememberedPhone.first())

        repo.saveRememberedLoginPhone(rememberMe = false, phone = "01711223344")
        assertFalse(repo.rememberMeEnabled.first())
        assertEquals("", repo.rememberedPhone.first())
    }

    @Test
    fun `forgot password button is placed directly below password box and above login button`() {
        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                AuthLoginScreen(
                    language = "en-US",
                    authReady = true,
                    authLoading = false,
                    isOnline = true,
                    rememberMeEnabled = true,
                    rememberedPhone = "01711223344",
                    onSaveRememberedPhone = { _, _ -> },
                    onLanguageSelected = {},
                    onGoogleSignIn = {}
                )
            }
        }
        composeRule.waitForIdle()

        val passwordBounds = composeRule.onNodeWithTag("login_password_input").getBoundsInRoot()
        val forgotBounds = composeRule.onNodeWithTag("forgot_password_button").getBoundsInRoot()
        val rememberBounds = composeRule.onNodeWithTag("remember_me_row").getBoundsInRoot()
        val loginBtnBounds = composeRule.onNodeWithTag("number_password_login_button").getBoundsInRoot()

        assertTrue(
            "Forgot password (${forgotBounds.top}) must be below password box (${passwordBounds.bottom})",
            forgotBounds.top >= passwordBounds.bottom
        )
        assertTrue(
            "Forgot password (${forgotBounds.bottom}) must be above Remember Me (${rememberBounds.top})",
            forgotBounds.bottom <= rememberBounds.top
        )
        assertTrue(
            "Remember Me (${rememberBounds.bottom}) must be above Login button (${loginBtnBounds.top})",
            rememberBounds.bottom <= loginBtnBounds.top
        )
        composeRule.onNodeWithText("01711223344").assertIsDisplayed()
    }

    @Test
    fun `forgot password reset fields validate email in Bengali and show Gmail launcher on success`() = runBlocking {
        var openedGmail = false
        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                var email by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                var sent by remember { mutableStateOf<String?>(null) }

                ForgotPasswordResetFields(
                    gmailInput = email,
                    busy = false,
                    errorMessage = error,
                    sentToEmail = sent,
                    isBengali = true,
                    onGmailChange = {
                        email = it
                        error = validateResetRequestInput(it, isOnline = true, isBengali = true).errorMessage
                        sent = if (error == null && it.isNotBlank()) it.trim() else null
                    },
                    onSubmit = {
                        val check = validateResetRequestInput(email, isOnline = true, isBengali = true)
                        error = check.errorMessage
                        sent = check.normalizedEmail
                    },
                    onOpenGmail = { openedGmail = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("forgot_password_email_input").performTextInput("invalid-email")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forgot_password_error_text").assertIsDisplayed()
        composeRule.onNodeWithText("সঠিক ইমেল / Gmail ঠিকানা দিন।").assertIsDisplayed()

        composeRule.onNodeWithTag("forgot_password_email_input").performTextInput("@gmail.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_gmail_button").assertIsDisplayed().performClick()
        assertTrue(openedGmail)

        val offlineCheck = validateResetRequestInput("user@gmail.com", isOnline = false, isBengali = true)
        assertNotNull(offlineCheck.errorMessage)
        val helperValidation = sendPasswordResetToGmail("bad", isBengali = true)
        assertTrue(helperValidation.isFailure)
    }

    @Test
    fun `Bengali login mode switches to create account and offline disables login actions`() {
        var isOnlineState by mutableStateOf(true)
        composeRule.setContent {
            ChatboxTheme(lavenderAppearance = false) {
                AuthLoginScreen(
                    language = "bn-BD",
                    authReady = true,
                    authLoading = false,
                    isOnline = isOnlineState,
                    onLanguageSelected = {},
                    onGoogleSignIn = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("মোবাইল নম্বর").assertIsDisplayed()
        composeRule.onNodeWithText("পাসওয়ার্ড").assertIsDisplayed()
        composeRule.onNodeWithText("পাসওয়ার্ড ভুলে গেছেন?").assertIsDisplayed()
        composeRule.onNodeWithText("আমার নম্বর মনে রাখুন").assertIsDisplayed()
        composeRule.onNodeWithText("লগ ইন করুন").assertIsDisplayed()

        composeRule.onNodeWithTag("create_new_account_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_confirm_password_input").assertIsDisplayed()
        composeRule.onNodeWithTag("back_to_login_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forgot_password_button").assertIsDisplayed()

        isOnlineState = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("offline_login_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("forgot_password_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("number_password_login_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("create_new_account_button").assertIsNotEnabled()
    }
}
