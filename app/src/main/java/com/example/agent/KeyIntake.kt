package com.example.agent

import android.content.Context
import android.util.Log
import com.example.data.repository.FirebaseProviders
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The owner just sends Saira his API keys in a chat message ("ei key gula bosay nao").
 * Keys are cut out of the message before it is saved or sent to any AI, tested, and installed:
 *  - Gemini keys → shared pool `settings/aiKeyPool` (every user's Saira, live within minutes)
 *  - Claude / GPT / … → owner-only vault `adminVault/sairaProKeys` + this phone (pro brain),
 *    which the GitHub coding agent also reads.
 */
object KeyIntake {
    private const val TAG = "KeyIntake"

    private val KEY_RE = Regex(
        "(?<![A-Za-z0-9_\\-.])(" +
            "AQ\\.[A-Za-z0-9_\\-]{30,}" +
            "|AIza[A-Za-z0-9_\\-]{30,}" +
            "|sk-ant-[A-Za-z0-9_\\-]{20,}" +
            "|sk-or-[A-Za-z0-9_\\-]{20,}" +
            "|deepseek:sk-[A-Za-z0-9_\\-]{16,}" +
            "|sk-[A-Za-z0-9_\\-]{20,}" +
            "|xai-[A-Za-z0-9_\\-]{20,}" +
            "|gsk_[A-Za-z0-9_\\-]{20,}" +
            ")"
    )

    data class Extracted(val keys: List<String>, val redacted: String)

    /** Pulls keys out of [text]; also undoes markdown escaping (`\_` → `_`). */
    fun extract(text: String): Extracted {
        val clean = text.replace("\\_", "_")
        val keys = KEY_RE.findAll(clean).map { it.value.trimEnd('.', ',', ';') }.distinct().toList()
        if (keys.isEmpty()) return Extracted(emptyList(), text)
        val redacted = KEY_RE.replace(clean, "").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        return Extracted(keys, redacted)
    }

    private fun isGemini(k: String) = k.startsWith("AQ.") || k.startsWith("AIza")

    /** "OK" | "QUOTA" (valid, rate-limited now) | "INVALID" | "NET" */
    private fun probeGemini(key: String): String {
        val conn = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"
        ).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "hi")))))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 8))
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) return "OK"
            val low = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8).orEmpty().lowercase()
            when {
                code == 429 || "resource_exhausted" in low || "quota" in low -> "QUOTA"
                code == 404 -> "QUOTA" // model name moved; key itself answered
                else -> "INVALID"
            }
        } catch (e: Exception) {
            "NET"
        } finally {
            conn.disconnect()
        }
    }

    /** Tests + installs keys; returns a Bengali report (keys are only shown masked). */
    suspend fun install(context: Context, keys: List<String>): String = withContext(Dispatchers.IO) {
        val gemini = keys.filter { isGemini(it) }
        SairaAgent.init(context)
        val pro = ProBrain.parse(keys.filterNot { isGemini(it) }.joinToString("\n"))
        val unknown = keys.size - gemini.size - pro.size
        val lines = mutableListOf<String>()
        val uid = FirebaseProviders.auth().currentUser?.uid
        val db = FirebaseProviders.firestore(context)

        if (gemini.isNotEmpty()) {
            val gate = Semaphore(6)
            val results = coroutineScope {
                gemini.map { k -> async { gate.withPermit { k to probeGemini(k) } } }.awaitAll()
            }
            val usable = results.filter { it.second == "OK" || it.second == "QUOTA" || it.second == "NET" }.map { it.first }
            val ok = results.count { it.second == "OK" }
            val quota = results.count { it.second == "QUOTA" }
            val bad = results.count { it.second == "INVALID" }
            var stored = false
            var poolSize = -1
            if (usable.isNotEmpty() && uid != null) {
                runCatching {
                    val ref = db.collection("settings").document("aiKeyPool")
                    ref.update(
                        mapOf(
                            "keys" to FieldValue.arrayUnion(*usable.toTypedArray()),
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "updatedBy" to uid
                        )
                    ).await()
                    poolSize = (ref.get().await().get("keys") as? List<*>)?.size ?: -1
                    stored = true
                }.onFailure { Log.w(TAG, "pool update failed", it) }
            }
            lines += "🔑 Gemini key ${gemini.size}টা: ✅ চালু $ok" +
                (if (quota > 0) " · ⏳ লিমিটে আছে (পরে চলবে) $quota" else "") +
                (if (bad > 0) " · ❌ বাতিল $bad" else "")
            lines += when {
                usable.isEmpty() -> "কোনো কাজের key পাইনি, তাই কিছু যোগ করিনি।"
                stored -> "✅ সবার Saira-র key pool-এ যোগ করেছি" + (if (poolSize > 0) " — এখন মোট $poolSize টা key" else "") +
                    "। কয়েক মিনিটের মধ্যে সবার অ্যাপে কাজ করবে, নতুন ভার্সন লাগবে না।"
                uid == null -> "⚠️ লগইন নেই, তাই pool-এ যোগ করতে পারিনি।"
                else -> "⚠️ pool-এ লিখতে পারিনি (Firebase অনুমতি)। একটু পরে আবার পাঠান।"
            }
        }

        if (pro.isNotEmpty()) {
            val tests = pro.map { k -> "${ProBrain.LABELS[k.provider]} ${ProBrain.mask(k.key)}: ${ProBrain.test(k)}" }
            lines += "🧠 শক্তিশালী মডেলের key:"
            lines += tests
            val local = SairaAgent.mergeProKeysLocally(pro)
            val vault = if (uid != null) runCatching {
                db.collection("adminVault").document("sairaProKeys").set(
                    mapOf(
                        "keys" to ProBrain.serialize(local),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "updatedBy" to uid
                    ),
                    SetOptions.merge()
                ).await()
            }.isSuccess else false
            lines += if (vault) {
                "🔒 গোপনে সংরক্ষণ করেছি — চ্যাট আর অ্যাপের কোড বদলানো (GitHub) দুটোতেই এখন এগুলো ব্যবহার করব।"
            } else {
                "🔒 এই ফোনে সংরক্ষণ করেছি (চ্যাটে কাজ করবে)। GitHub-এর কাজের জন্য Firebase-এ রাখা যায়নি — পরে আবার পাঠালে চেষ্টা করব।"
            }
        }
        if (unknown > 0) lines += "❓ $unknown টা key চিনতে পারিনি।"
        lines += "(নিরাপত্তার জন্য key গুলো চ্যাট থেকে মুছে দিয়েছি।)"
        lines.joinToString("\n")
    }
}
