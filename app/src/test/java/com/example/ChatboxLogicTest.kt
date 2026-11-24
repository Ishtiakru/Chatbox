package com.example

import com.example.data.ChatboxCatalog
import com.example.data.SparkAdminCommands
import com.example.data.SparkCommandProposal
import com.example.data.ai.AdminPlanInterpreter
import com.example.data.ai.SairaPrompts
import com.example.data.model.ChatboxConstants
import com.example.data.model.DisappearingOption
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
    fun `offline saira guide replies and e2ee safety numbers work consistently`() {
        assertTrue(ChatboxCatalog.getSairaOfflineGuideReply("কমান্ড কি কি").contains("১ মিনিটের"))
        assertTrue(ChatboxCatalog.getSairaOfflineGuideReply("hi").contains("Saira"))
        assertTrue(ChatboxCatalog.getSairaOfflineGuideReply("pin").contains("123456"))
        assertTrue(ChatboxCatalog.getSairaOfflineGuideReply("update version").contains(ChatboxConstants.APP_VERSION))
        assertTrue(ChatboxCatalog.getSairaOfflineGuideReply("firebase").contains("Firebase"))

        val blocks1 = ChatboxCatalog.buildSafetyNumberBlocks("sazzad", "Sazzad Jony")
        val blocks2 = ChatboxCatalog.buildSafetyNumberBlocks("sazzad", "Sazzad Jony")
        assertEquals(12, blocks1.size)
        assertEquals(blocks1, blocks2)
        assertTrue(blocks1.all { it.length == 5 && it.all(Char::isDigit) })

        assertEquals(DisappearingOption.OFF, DisappearingOption.fromKey("unknown"))
        assertEquals(DisappearingOption.HOURS_24, DisappearingOption.fromKey("24h"))
        assertEquals(DisappearingOption.DAYS_7, DisappearingOption.fromKey("7d"))
        assertEquals(DisappearingOption.DAYS_90, DisappearingOption.fromKey("90d"))
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

    @Test
    fun `saira user directory prioritizes self deduplicates drops blanks and caps at max users`() {
        val self = com.example.saira.SairaDirectoryUser(
            uid = "uid_self",
            name = " Sojib ",
            shortId = " 123456 ",
            about = " Hey there ",
            isSelf = true
        )
        val peers = buildList {
            add(com.example.saira.SairaDirectoryUser(uid = "uid_self", name = "Duplicate Self", shortId = "999999"))
            add(com.example.saira.SairaDirectoryUser(uid = "uid_blank", name = "   ", shortId = "111111"))
            add(com.example.saira.SairaDirectoryUser(uid = "   ", name = "No Uid", shortId = "222222"))
            for (i in 1..45) {
                add(com.example.saira.SairaDirectoryUser(uid = "peer_$i", name = "User $i", shortId = "%06d".format(i)))
            }
            add(com.example.saira.SairaDirectoryUser(uid = "peer_1", name = "Duplicate Peer", shortId = "888888"))
        }

        val built = com.example.saira.SairaUserDirectory.build(self = self, peers = peers)
        assertEquals(com.example.saira.SairaUserDirectory.MAX_USERS, built.size)
        assertEquals("uid_self", built.first().uid)
        assertEquals("Sojib", built.first().name)
        assertEquals("123456", built.first().shortId)
        assertTrue(built.first().isSelf)
        assertEquals(built.size, built.map { it.uid }.toSet().size)
        assertFalse(built.any { it.uid == "uid_blank" })
    }

    @Test
    fun `bootstrapped owner email and phone lookup edge cases behave consistently`() {
        assertEquals(setOf("sojibsheikhuk@gmail.com"), ChatboxConstants.BOOTSTRAPPED_OWNER_EMAILS)
        assertTrue(LiveChatRepository.buildPhoneLookupCandidates("123").isEmpty())
        val intlCandidates = LiveChatRepository.buildPhoneLookupCandidates("+8801712345678")
        assertTrue("8801712345678" in intlCandidates)
        assertTrue("01712345678" in intlCandidates)
    }
}
