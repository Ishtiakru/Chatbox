package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailRecoveryRepositoryTest {
    @Test
    fun `secret round trips only for the same login`() {
        val login = "8801712345678@phone.chatbox.app"
        val enc = GmailRecoveryRepository.encrypt(login, "Pass@123")
        assertNotEquals("Pass@123", enc)
        assertEquals("Pass@123", GmailRecoveryRepository.decrypt(login, enc))
        val wrong = runCatching { GmailRecoveryRepository.decrypt("8801999999999@phone.chatbox.app", enc) }
        assertTrue(wrong.isFailure)
    }

    @Test
    fun `gmail addresses are normalized and validated`() {
        assertEquals("me.x@gmail.com", GmailRecoveryRepository.normalize("  Me.X@Gmail.com "))
        assertNull(GmailRecoveryRepository.normalize("me@yahoo.com"))
        assertNull(GmailRecoveryRepository.normalize("not an email"))
    }
}
