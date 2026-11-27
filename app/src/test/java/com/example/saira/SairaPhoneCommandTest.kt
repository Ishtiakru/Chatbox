package com.example.saira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SairaPhoneCommandTest {
    private fun p(s: String) = SairaPhoneCommandParser.parse(s)

    @Test
    fun `phone calls by number in Bengali Banglish and English`() {
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "01712345678"), p("01712345678 নম্বরে কল দাও"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "01712345678"), p("০১৭১২৩৪৫৬৭৮ এ কল দাও"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "01712345678"), p("call 01712345678"))
    }

    @Test
    fun `app calls and video calls by name`() {
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.WHATSAPP, "karim"), p("whatsapp e karim ke call dao"))
        assertEquals(PhoneCommand(PhoneAction.VIDEO_CALL, PhoneApp.WHATSAPP, "করিম"), p("করিমকে হোয়াটসঅ্যাপে ভিডিও কল দাও"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.IMO, "abbu"), p("imo te abbu ke call dao"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.WHATSAPP, "mom"), p("call my mom on whatsapp"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "karim"), p("karim er number e call dao"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "আম্মু"), p("সায়রা, আম্মুকে কল দাও"))
    }

    @Test
    fun `messages with text`() {
        assertEquals(PhoneCommand(PhoneAction.MESSAGE, PhoneApp.SMS, "আম্মু", "আমি আসছি"), p("আম্মুকে মেসেজ দাও যে আমি আসছি"))
        assertEquals(PhoneCommand(PhoneAction.MESSAGE, PhoneApp.TELEGRAM, "karim", "I'm late"), p("message karim on telegram: I'm late"))
        assertEquals(
            PhoneCommand(PhoneAction.MESSAGE, PhoneApp.WHATSAPP, "Rahim bhai", "ami 5 minute e asbo"),
            p("Rahim bhai ke whatsapp e message dao je ami 5 minute e asbo")
        )
        assertEquals(PhoneCommand(PhoneAction.MESSAGE, PhoneApp.SMS, "01812345678", "ami pouche gechi"), p("01812-345678 e sms pathao \"ami pouche gechi\""))
        val noText = p("whatsapp e message dao")!!
        assertTrue(noText.needsTarget && noText.needsText)
    }

    @Test
    fun `answer decline screen and apps`() {
        listOf("call ta dhoro", "dhoro", "কল ধরো", "receive koro").forEach { assertEquals(it, PhoneAction.ANSWER, p(it)?.action) }
        listOf("কেটে দাও", "call kete dao", "reject").forEach { assertEquals(it, PhoneAction.DECLINE, p(it)?.action) }
        assertEquals(PhoneAction.SCREEN_OFF, p("screen off koro")?.action)
        assertEquals(PhoneAction.SCREEN_ON, p("স্ক্রিন অন করো")?.action)
        assertEquals(PhoneCommand(PhoneAction.OPEN_APP, PhoneApp.OTHER, "youtube"), p("youtube kholo"))
    }

    @Test
    fun `real speech-to-text transcripts`() {
        // Exactly what Gemini flash-lite returned for spoken commands (CI probe).
        fun voice(t: String) = SairaWakeWord.extract(t)?.let { p(it) }
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "আম্মু"), voice("Saira, আম্মুকে কল দাও।"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.WHATSAPP, "mom"), voice("Saira, call my mom on WhatsApp."))
        assertEquals(PhoneAction.SCREEN_OFF, voice("Saira screen bondho koro")?.action)
        assertEquals(PhoneAction.SCREEN_OFF, voice("Saira স্ক্রিন বন্ধ করো")?.action)
        assertEquals("কল ধরো", SairaWakeWord.extract("Okay Saira, কল ধরো"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "ammu"), voice("Saira, ammuker call dao."))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "abbu"), voice("Saira, abbur kache call dao"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "আম্মু"), voice("সায়রা আম্মুর কাছে কল দাও"))
        assertEquals(PhoneCommand(PhoneAction.CALL, PhoneApp.PHONE, "ammu"), voice("Sarah, call ammu"))
        assertEquals("", voice("Saira ekbar call dao")?.target)
    }

    @Test
    fun `ordinary chat is never treated as a phone command`() {
        listOf(
            "বাংলায় একটি সুন্দর সময় অনুযায়ী সম্ভাষণ ও বার্তা লিখে দাও",
            "Explain quantum computing simply in 3 points",
            "Write a friendly reply when I am busy at work",
            "ke call korechilo?",
            "ekta kobita likho",
            "সকল মানুষ সমান",
            "dhoro ami tomake ekta prosno kori",
            "translate this text to english",
            "how do I make a video call in chatbox?",
            "ami kal karim ke call korbo"
        ).forEach { assertNull(it, p(it)) }
    }

    @Test
    fun `yes no wake word and number formatting`() {
        listOf("হ্যাঁ", "ha", "haa koro", "ok", "thik ache").forEach { assertTrue(it, SairaPhoneCommandParser.isYes(it)) }
        listOf("না", "na", "cancel", "থাক").forEach { assertTrue(it, SairaPhoneCommandParser.isNo(it)) }
        assertFalse(SairaPhoneCommandParser.isYes("karim ke call dao"))
        assertEquals("call ammu", SairaWakeWord.extract("Saira, call ammu"))
        assertEquals("আম্মুকে কল দাও", SairaWakeWord.extract("সায়রা আম্মুকে কল দাও"))
        assertEquals("", SairaWakeWord.extract("hey saira"))
        assertNull(SairaWakeWord.extract("সারা দিন কাজ করেছি"))
        assertNull(SairaWakeWord.extract("আমি সায়রাকে বলেছি"))
        assertEquals("8801712345678", SairaPhoneCommandParser.internationalDigits("01712345678", "bd"))
        assertEquals("8801712345678", SairaPhoneCommandParser.internationalDigits("+880 1712-345678", null))
        assertEquals(
            PhoneCommand(PhoneAction.VIDEO_CALL, PhoneApp.WHATSAPP, "karim"),
            SairaPhoneCommandParser.fromFields("video_call", "whatsapp", "karim", "")
        )
        assertNull(SairaPhoneCommandParser.fromFields("none", "phone", "", ""))
    }
}
