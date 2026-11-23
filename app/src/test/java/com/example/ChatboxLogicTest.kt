package com.example

import com.example.data.DemoCatalog
import com.example.data.SparkAdminCommands
import com.example.data.SparkCommandProposal
import com.example.data.ai.AdminPlanInterpreter
import com.example.data.ai.SairaPrompts
import com.example.data.model.ChatboxConstants
import com.example.data.model.WorldLanguages
import com.example.data.repository.LiveChatRepository
import com.example.saira.SairaTimeGreeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatboxLogicTest {

    @Test
    fun `world languages normalize and detect Bengali cleanly`() {
        assertEquals("bn-BD", WorldLanguages.normalizeCode("bn-BD"))
        assertEquals("bn-BD", WorldLanguages.normalizeCode("bn"))
        assertEquals("en-US", WorldLanguages.normalizeCode("unknown-XX"))
        assertTrue(WorldLanguages.isBengali("bn-BD"))
        assertTrue(WorldLanguages.isBengali("bn-IN"))
        assertFalse(WorldLanguages.isBengali("en-US"))
    }

    @Test
    fun `spark admin commands parse English Bengali and Banglish dashboard inputs`() {
        assertEquals(
            SparkCommandProposal.SetTheme("lavender"),
            SparkAdminCommands.parse("আমার হোম পেজটা ল্যাভেন্ডার কালার করো")
        )
        assertEquals(
            SparkCommandProposal.SetTheme("lavender"),
            SparkAdminCommands.parse("home lavender koro")
        )
        assertEquals(
            SparkCommandProposal.SetTheme("default"),
            SparkAdminCommands.parse("theme: default")
        )
        assertEquals(
            SparkCommandProposal.SetMenu("top"),
            SparkAdminCommands.parse("মেনুবার উপরে নিয়ে আসো")
        )
        assertEquals(
            SparkCommandProposal.SetMenu("top"),
            SparkAdminCommands.parse("menu upore nao")
        )
        assertEquals(
            SparkCommandProposal.SetMenu("bottom"),
            SparkAdminCommands.parse("menu: bottom")
        )
        assertEquals(
            SparkCommandProposal.SetAppTitle("Chatbox Pro"),
            SparkAdminCommands.parse("title: Chatbox Pro")
        )
        assertEquals(
            SparkCommandProposal.RemoveMenu("notice"),
            SparkAdminCommands.parse("remove menu notice")
        )
        assertEquals(
            SparkCommandProposal.SetAppUpdate(
                active = true,
                version = "1.2.0",
                note = "Fast build",
                downloadUrl = "https://github.com/Ishtiakru/Chatbox/releases/tag/v1.2.0"
            ),
            SparkAdminCommands.parse(
                "update: 1.2.0 | Fast build | https://github.com/Ishtiakru/Chatbox/releases/tag/v1.2.0"
            )
        )
        assertEquals(
            SparkCommandProposal.SetAppUpdate(active = false, version = "", note = "", downloadUrl = ""),
            SparkAdminCommands.parse("update: off")
        )
        assertTrue(SparkAdminCommands.isValidSparkTarget("Partner.Admin@gmail.com"))
        assertFalse(SparkAdminCommands.isValidSparkTarget("user@yahoo.com"))
    }

    @Test
    fun `whatsapp style short id and phone helpers produce deterministic valid keys`() {
        val shortId1 = LiveChatRepository.deterministicSixDigitSeed("user_uid_alpha")
        val shortId2 = LiveChatRepository.deterministicSixDigitSeed("user_uid_alpha")
        assertEquals(shortId1, shortId2)
        assertTrue(ChatboxConstants.VALID_SHORT_ID_REGEX.matches(shortId1))

        assertEquals("uidA_uidB", LiveChatRepository.buildConversationId("uidB", "uidA"))
        assertEquals("8801712345678", LiveChatRepository.normalizePhoneDigits("+880 1712-345678"))

        val bdCandidates = LiveChatRepository.buildPhoneLookupCandidates("01712345678")
        assertTrue("01712345678" in bdCandidates)
        assertTrue("8801712345678" in bdCandidates)
    }

    @Test
    fun `offline saira demo guide replies in Bengali for core topics`() {
        assertTrue(DemoCatalog.getSairaOfflineGuideReply("কমান্ড কি কি").contains("১ মিনিটের"))
        assertTrue(DemoCatalog.getSairaOfflineGuideReply("hi").contains("Saira"))
        assertTrue(DemoCatalog.getSairaOfflineGuideReply("অ্যাডমিন প্যানেল").contains("অ্যাডমিন"))
        assertTrue(DemoCatalog.getSairaOfflineGuideReply("update version").contains(ChatboxConstants.APP_VERSION))
        assertTrue(DemoCatalog.getSairaOfflineGuideReply("firebase").contains("Firebase"))
    }

    @Test
    fun `admin plan heuristic extracts Bengali and Banglish custom menu bars cleanly`() {
        val noticePlan = AdminPlanInterpreter.fallbackHeuristicPlan(
            "একটা নতুন মেনু বার যোগ করো নাম Notice, প্রতিদিন রাত ১০টায় লাইভ আড্ডা"
        )
        assertEquals("Notice", noticePlan.addOrUpdateMenu?.label)
        assertEquals("notice", noticePlan.addOrUpdateMenu?.id)
        assertEquals("bell", noticePlan.addOrUpdateMenu?.icon)

        val banglishMenuPlan = AdminPlanInterpreter.fallbackHeuristicPlan(
            "ak nemu bar add koro name Rules"
        )
        assertEquals("Rules", banglishMenuPlan.addOrUpdateMenu?.label)
        assertEquals("rules", banglishMenuPlan.addOrUpdateMenu?.id)
    }

    @Test
    fun `time greeting and prompts cover all day periods and sender name check-in`() {
        assertEquals("সুপ্রভাত", SairaTimeGreeting.infoForHour(8).bengaliGreeting)
        assertEquals("শুভ দুপুর", SairaTimeGreeting.infoForHour(13).bengaliGreeting)
        assertEquals("শুভ সন্ধ্যা", SairaTimeGreeting.infoForHour(19).bengaliGreeting)
        assertEquals("শুভ রাত্রি", SairaTimeGreeting.infoForHour(23).bengaliGreeting)
        assertTrue(SairaTimeGreeting.infoForHour(2).isNightTime)

        val absenceMsg = SairaTimeGreeting.buildOneMinuteAbsenceFallback(
            senderName = "সজীব",
            peerName = "ইশতিয়াক",
            hourOfDay = 19
        )
        assertTrue(absenceMsg.startsWith("হ্যালো সজীব! শুভ সন্ধ্যা"))

        val autoPrompt = SairaPrompts.buildNotificationAutoReplyPrompt(
            senderName = "Rahima",
            platformLabel = "WhatsApp",
            incomingMessage = "Where are you?",
            taughtGuidance = "আমি মিটিংয়ে আছি",
            locationText = null,
            hourOfDay = 9
        )
        assertTrue(autoPrompt.contains("সুপ্রভাত"))
        assertTrue(autoPrompt.contains("NOT permitted"))
    }
}
