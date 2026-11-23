package com.example.data.ai

import com.example.data.SparkAdminCommands
import com.example.data.SparkCommandProposal
import com.example.data.model.AdminAiUpdatePlan
import com.example.data.model.ChatboxConstants
import com.example.data.model.CustomMenuIconName
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkCustomMenu
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

object AdminPlanInterpreter {
    private val adminInterpreterInstruction = """
        You are Chatbox Admin App Controller. Convert the admin's natural-language request (in Bengali, Banglish, or English) into a structured JSON update plan for the live Chatbox app.

        Rules:
        - Only set fields the admin asked to change; leave unchanged optional fields null/omitted.
        - theme: "default" or "lavender"
        - menuPosition: "bottom" or "top"
        - welcomeBanner: string (max 160 chars)
        - appTitle: string (max 40 chars)
        - homeAnnouncement: string (max 240 chars)
        - addOrUpdateMenu: { id (lowercase slug 2..24 chars), label (short 1-2 word tab label max 20 chars), icon ("sparkles"|"bell"|"megaphone"|"book"|"star"|"info"|"grid"), title (max 80 chars), body (max 900 chars) }
        - removeMenuId: slug id if asked to delete a custom menu
        - appUpdate: { active (bool), version (max 32 chars), note (max 240 chars) }
        - summary: concise 1-sentence confirmation in the same language the admin wrote (Bengali, Banglish, or English).
    """.trimIndent()

    private val schema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(),
            "theme" to Schema.enumeration(listOf("default", "lavender")),
            "menuPosition" to Schema.enumeration(listOf("bottom", "top")),
            "welcomeBanner" to Schema.string(),
            "appTitle" to Schema.string(),
            "homeAnnouncement" to Schema.string(),
            "removeMenuId" to Schema.string(),
            "addOrUpdateMenu" to Schema.obj(
                properties = mapOf(
                    "id" to Schema.string(),
                    "label" to Schema.string(),
                    "icon" to Schema.enumeration(
                        CustomMenuIconName.entries.map { it.key }
                    ),
                    "title" to Schema.string(),
                    "body" to Schema.string()
                ),
                optionalProperties = emptyList()
            ),
            "appUpdate" to Schema.obj(
                properties = mapOf(
                    "active" to Schema.boolean(),
                    "version" to Schema.string(),
                    "note" to Schema.string()
                ),
                optionalProperties = listOf("note")
            )
        ),
        optionalProperties = listOf(
            "theme",
            "menuPosition",
            "welcomeBanner",
            "appTitle",
            "homeAnnouncement",
            "removeMenuId",
            "addOrUpdateMenu",
            "appUpdate"
        )
    )

    suspend fun interpret(
        instruction: String,
        currentMenus: List<SparkCustomMenu>
    ): AdminAiUpdatePlan {
        val trimmed = instruction.trim()
        require(trimmed.isNotEmpty()) { "Instruction cannot be empty." }

        // Fast deterministic parse for explicit shorthand commands
        when (val direct = SparkAdminCommands.parse(trimmed)) {
            is SparkCommandProposal.SetTheme -> return AdminAiUpdatePlan(
                summary = "হোম থিম '${direct.value}'-এ পরিবর্তনের জন্য প্রস্তুত।",
                theme = direct.value
            )
            is SparkCommandProposal.SetMenu -> return AdminAiUpdatePlan(
                summary = "নেভিগেশন মেনু '${direct.value}'-এ নেওয়ার জন্য প্রস্তুত।",
                menuPosition = direct.value
            )
            is SparkCommandProposal.SetWelcome -> return AdminAiUpdatePlan(
                summary = "স্বাগতম বার্তা আপডেট করার জন্য প্রস্তুত।",
                welcomeBanner = direct.value
            )
            is SparkCommandProposal.SetAppUpdate -> return AdminAiUpdatePlan(
                summary = if (direct.active) "নতুন ভার্সন v${direct.version} আপডেট প্রকাশের জন্য প্রস্তুত।" else "সবুজ Update বাটন বন্ধ করার জন্য প্রস্তুত।",
                appUpdate = SparkAppUpdate(
                    active = direct.active,
                    version = direct.version,
                    note = direct.note,
                    downloadUrl = direct.downloadUrl
                )
            )
            SparkCommandProposal.Unsupported -> Unit
        }

        val existingMenuSummary = if (currentMenus.isEmpty()) {
            "none"
        } else {
            currentMenus.joinToString(", ") { "${it.id}(${it.label})" }
        }

        return try {
            val model = Firebase.ai.generativeModel(
                modelName = GeminiAiProvider.DEFAULT_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                    maxOutputTokens = 1024
                },
                systemInstruction = content { text(adminInterpreterInstruction) }
            )
            val prompt = """
                Existing custom menus: $existingMenuSummary
                Admin instruction:
                "$trimmed"
            """.trimIndent()

            val responseText = withTimeout(60_000L) {
                model.generateContent(prompt).text.orEmpty()
            }
            parseJsonPlan(responseText, trimmed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            fallbackHeuristicPlan(trimmed)
        }
    }

    private fun parseJsonPlan(rawJson: String, originalInstruction: String): AdminAiUpdatePlan {
        val root = JSONObject(rawJson)
        val summary = root.optString("summary").trim()
            .ifEmpty { "আপনার নির্দেশ অনুযায়ী লাইভ অ্যাপ আপডেট করার জন্য প্রস্তুত।" }

        val theme = root.optString("theme").takeIf { it == "default" || it == "lavender" }
        val menuPos = root.optString("menuPosition").takeIf { it == "top" || it == "bottom" }
        val welcomeBanner = if (root.has("welcomeBanner") && !root.isNull("welcomeBanner")) {
            root.optString("welcomeBanner").trim().take(ChatboxConstants.MAX_WELCOME_LENGTH)
                .takeIf { it.isNotEmpty() }
        } else null
        val appTitle = if (root.has("appTitle") && !root.isNull("appTitle")) {
            root.optString("appTitle").trim().take(40).takeIf { it.isNotEmpty() }
        } else null
        val homeAnnouncement = if (root.has("homeAnnouncement") && !root.isNull("homeAnnouncement")) {
            root.optString("homeAnnouncement").trim().take(240)
        } else null
        val removeMenuId = if (root.has("removeMenuId") && !root.isNull("removeMenuId")) {
            root.optString("removeMenuId").trim().lowercase().take(32).takeIf { it.isNotEmpty() }
        } else null

        val menuObj = root.optJSONObject("addOrUpdateMenu")
        val addOrUpdateMenu = if (menuObj != null) {
            val rawId = menuObj.optString("id").lowercase().replace(Regex("[^a-z0-9_-]"), "").take(32)
            val label = menuObj.optString("label").trim().take(24)
            val title = menuObj.optString("title").trim().ifEmpty { label }.take(80)
            val body = menuObj.optString("body").trim().ifEmpty { originalInstruction }.take(1000)
            val icon = CustomMenuIconName.fromKey(menuObj.optString("icon")).key
            if (rawId.isNotEmpty() && label.isNotEmpty()) {
                SparkCustomMenu(id = rawId, label = label, icon = icon, title = title, body = body)
            } else null
        } else null

        val updObj = root.optJSONObject("appUpdate")
        val appUpdate = if (updObj != null) {
            SparkAppUpdate(
                active = updObj.optBoolean("active", false),
                version = updObj.optString("version").trim().take(ChatboxConstants.MAX_UPDATE_VERSION_LENGTH),
                note = updObj.optString("note").trim().take(ChatboxConstants.MAX_UPDATE_NOTE_LENGTH),
                downloadUrl = ""
            )
        } else null

        return AdminAiUpdatePlan(
            summary = summary,
            theme = theme,
            menuPosition = menuPos,
            welcomeBanner = welcomeBanner,
            appTitle = appTitle,
            homeAnnouncement = homeAnnouncement,
            addOrUpdateMenu = addOrUpdateMenu,
            removeMenuId = removeMenuId,
            appUpdate = appUpdate
        )
    }

    private fun fallbackHeuristicPlan(instruction: String): AdminAiUpdatePlan {
        val lower = instruction.lowercase()
        val theme = when {
            lower.contains("lavender") || instruction.contains("ল্যাভেন্ডার") -> "lavender"
            lower.contains("default theme") || instruction.contains("ডিফল্ট") -> "default"
            else -> null
        }
        val menuPos = when {
            lower.contains("menu top") || instruction.contains("উপরে") -> "top"
            lower.contains("menu bottom") || instruction.contains("নিচে") -> "bottom"
            else -> null
        }
        val wantsMenu = lower.contains("menu") || instruction.contains("মেনু")
        val menu = if (wantsMenu) {
            val slug = "menu-${(instruction.hashCode() ushr 1) % 900 + 100}"
            val label = when {
                instruction.contains("নোটিশ") || lower.contains("notice") -> "Notice"
                instruction.contains("নিয়ম") || lower.contains("rule") -> "Rules"
                instruction.contains("অফার") || lower.contains("offer") -> "Offers"
                else -> "Updates"
            }
            SparkCustomMenu(
                id = slug,
                label = label,
                icon = "bell",
                title = label,
                body = instruction.take(900)
            )
        } else null

        if (theme == null && menuPos == null && menu == null) {
            return AdminAiUpdatePlan(
                summary = "হোম স্ক্রিনে ঘোষণা হিসেবে যুক্ত করার জন্য প্রস্তুত।",
                homeAnnouncement = instruction.take(240)
            )
        }

        return AdminAiUpdatePlan(
            summary = "নির্দেশনা অনুযায়ী অ্যাপ কনফিগারেশন আপডেটের জন্য প্রস্তুত।",
            theme = theme,
            menuPosition = menuPos,
            addOrUpdateMenu = menu
        )
    }
}
