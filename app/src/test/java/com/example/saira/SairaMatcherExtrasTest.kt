package com.example.saira

import com.example.data.ai.AiSafeResponses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SairaMatcherExtrasTest {

    private fun match(sender: String, rules: List<SairaNotificationRule>, pkg: String = "com.whatsapp") =
        SairaNotificationMatcher.match(
            notifAutoReplyEnabled = true,
            liveLocationEnabled = false,
            locationPermissionGranted = false,
            packageName = pkg,
            senderTitle = sender,
            incomingText = "Kemon acho?",
            isGroupSummary = false,
            rules = rules
        )

    @Test
    fun `everyone rule answers any sender but specific rules win`() {
        val everyone = SairaNotificationRule(id = "all", contactName = "সবাই", taughtReplyGuidance = "আমি ব্যস্ত, পরে কথা বলব।")
        val sajib = SairaNotificationRule(id = "s", contactName = "Sajib", taughtReplyGuidance = "সজীব, একটু পর কল দিচ্ছি।")

        val stranger = match("Unknown Stranger", listOf(everyone, sajib))
        assertNotNull(stranger)
        assertEquals("all", stranger!!.rule.id)

        val specific = match("Sajib", listOf(everyone, sajib))
        assertEquals("s", specific!!.rule.id)

        listOf("*", "everyone", "All", "সবাই").forEach { name ->
            assertTrue(name, SairaNotificationMatcher.isEveryoneRule(name))
        }
        assertFalse(SairaNotificationMatcher.isEveryoneRule("Sajib"))
    }

    @Test
    fun `rule name matches saved contact with extra words`() {
        val rahima = SairaNotificationRule(id = "r", contactName = "Rahima", taughtReplyGuidance = "একটু পর কথা বলছি।")
        assertNotNull(match("Rahima Apu", listOf(rahima)))
        assertNotNull(match("Md Rahima Khatun", listOf(rahima), pkg = "com.facebook.orca"))
        assertNull(match("Rahimabad Store", listOf(rahima)))
        assertNull(match("Karim", listOf(rahima)))
    }

    @Test
    fun `safety fallback messages are detected so they are never sent to contacts`() {
        assertTrue(AiSafeResponses.isSafetyMessage(AiSafeResponses.forError(null)))
        assertTrue(AiSafeResponses.isSafetyMessage(AiSafeResponses.APP_CHECK_UNAVAILABLE))
        assertFalse(AiSafeResponses.isSafetyMessage("ভালো আছি, তুমি কেমন আছো?"))
        assertFalse(AiSafeResponses.isSafetyMessage(""))
    }

    @Test
    fun `default everyone rule answers anyone with the polite default text`() {
        val rule = SairaNotificationMatcher.defaultEveryoneRule()
        val m = match("Any Random Person", listOf(rule), pkg = "com.facebook.orca")
        assertNotNull(m)
        val reply = SairaNotificationMatcher.buildFirstBoxReply("Any Random Person", m!!.rule, null, hourOfDay = 21)
        assertTrue(reply.contains(SairaNotificationMatcher.DEFAULT_EVERYONE_TEXT))
    }
}
