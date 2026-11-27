package com.example.saira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SairaVoiceBrainParseTest {
    @Test
    fun `transcript and quick reply are split`() {
        val u = SairaVoiceBrain.parse("T: সায়রা তুমি কেমন আছো?\nR: আমি খুব ভালো আছি! তোমার দিনটা কেমন কাটছে?", wantReply = true)
        assertEquals("সায়রা তুমি কেমন আছো?", u.transcript)
        assertEquals("আমি খুব ভালো আছি! তোমার দিনটা কেমন কাটছে?", u.reply)
    }

    @Test
    fun `commands and transcribe-only give no reply`() {
        val c = SairaVoiceBrain.parse("T: Saira, ammuker call dao.\nR: -", wantReply = true)
        assertEquals("Saira, ammuker call dao.", c.transcript)
        assertNull(c.reply)
        assertNull(SairaVoiceBrain.parse("T: হ্যাঁ\nR: ঠিক আছে", wantReply = false).reply)
        assertEquals("plain words", SairaVoiceBrain.parse("plain words", wantReply = true).transcript)
    }

    @Test
    fun `owner is never called Saira in the reply`() {
        assertEquals(
            "আজ তো শুক্রবার। কোনো বিশেষ পরিকল্পনা আছে নাকি?",
            SairaVoiceBrain.parse("T: সায়রা আজ কি বার\nR: আজ তো শুক্রবার, সায়রা। কোনো বিশেষ পরিকল্পনা আছে নাকি?", true).reply
        )
        assertEquals("Aaj shukrobar!", SairaVoiceBrain.parse("T: x\nR: Aaj shukrobar, Saira!", true).reply)
    }
}
