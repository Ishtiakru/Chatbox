package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.ui.theme.ChatboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthLoginScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `login screen hides tagline and small captions and places create account at bottom`() {
        var googleClicks = 0
        var createAccountClicks = 0
        var queuedPhone: String? = null

        composeRule.setContent {
            ChatboxTheme {
                AuthLoginScreen(
                    languageCode = "en-US",
                    authLoading = false,
                    authError = null,
                    isOnline = true,
                    rememberMeEnabled = false,
                    rememberedPhone = "",
                    onSaveRememberedPhone = { _, _ -> },
                    onQueuePendingPhone = { queuedPhone = it },
                    onLanguageSelected = {},
                    onGoogleSignIn = { googleClicks++ },
                    onChooseGoogleAccount = { createAccountClicks++ },
                    onSendPasswordReset = { Result.success(Unit) }
                )
            }
        }
        composeRule.waitForIdle()

        // Brand title present; tagline & bottom captions removed
        composeRule.onNodeWithText("Chatbox").assertIsDisplayed()
        composeRule.onNodeWithText("A calmer space to stay close").assertDoesNotExist()
        composeRule.onNodeWithText("Choose a different Gmail account").assertDoesNotExist()
        composeRule.onNodeWithText("Create new account").assertDoesNotExist()
        composeRule.onNodeWithText("Pick any Gmail account on this device without auto-connecting.")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Chatbox · Secured by Firebase Auth & Cloud Firestore")
            .assertDoesNotExist()

        // Create account button in bottom spot triggers account picker / creation flow
        composeRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("+8801712345678")
        composeRule.onNodeWithTag("create_account_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, createAccountClicks)
        assertEquals("+8801712345678", queuedPhone)

        // Continue with Google triggers primary Google Sign-In
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, googleClicks)
    }

    @Test
    fun `phone login validates inputs and opens info and language dialogs`() {
        var googleClicks = 0
        var savedRememberMe: Pair<Boolean, String>? = null
        var selectedLanguage: String? = null

        composeRule.setContent {
            ChatboxTheme {
                AuthLoginScreen(
                    languageCode = "en-US",
                    authLoading = false,
                    authError = null,
                    isOnline = true,
                    rememberMeEnabled = false,
                    rememberedPhone = "",
                    onSaveRememberedPhone = { rem, phone -> savedRememberMe = rem to phone },
                    onQueuePendingPhone = {},
                    onLanguageSelected = { selectedLanguage = it },
                    onGoogleSignIn = { googleClicks++ },
                    onChooseGoogleAccount = {},
                    onSendPasswordReset = { Result.success(Unit) }
                )
            }
        }
        composeRule.waitForIdle()

        // Short phone validation
        composeRule.onNodeWithTag("phone_login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enter a valid phone number.").assertIsDisplayed()
        assertEquals(0, googleClicks)

        // Valid phone + valid password submits cleanly
        composeRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01712345678")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("secret123")
        composeRule.onNodeWithTag("phone_login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, googleClicks)
        assertTrue(savedRememberMe?.second == "01712345678")

        // Info dialog still accessible
        composeRule.onNodeWithText("Info").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sign-in information").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        // Language picker opens and switches language
        composeRule.onNodeWithTag("language_selector_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_search_input").performTextInput("Bangla")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("বাংলা (বাংলাদেশ)", substring = true).performClick()
        composeRule.waitForIdle()
        assertEquals("bn-BD", selectedLanguage)
    }

    @Test
    fun `bengali login screen omits tagline and small captions`() {
        var createClicks = 0

        composeRule.setContent {
            ChatboxTheme {
                AuthLoginScreen(
                    languageCode = "bn-BD",
                    authLoading = false,
                    authError = null,
                    isOnline = true,
                    rememberMeEnabled = false,
                    rememberedPhone = "",
                    onSaveRememberedPhone = { _, _ -> },
                    onQueuePendingPhone = {},
                    onLanguageSelected = {},
                    onGoogleSignIn = {},
                    onChooseGoogleAccount = { createClicks++ },
                    onSendPasswordReset = { Result.success(Unit) }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("কাছের মানুষদের সাথে থাকার একটি শান্ত জায়গা").assertDoesNotExist()
        composeRule.onNodeWithText("অন্য Gmail অ্যাকাউন্ট বাছাই করুন").assertDoesNotExist()
        composeRule.onNodeWithText("ফোনে লগইন থাকা যেকোনো Gmail অ্যাকাউন্ট তালিকা থেকে বেছে নিতে পারবেন।")
            .assertDoesNotExist()

        composeRule.onNodeWithTag("create_account_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, createClicks)
    }
}
