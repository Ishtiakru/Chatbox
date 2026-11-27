package com.example.ui.i18n

import android.content.Context
import android.util.Log
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.example.agent.SairaAgentAi
import com.example.data.model.WorldLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-app language switch.
 *
 * The app's texts are written directly in code (Bengali + English), so changing
 * the Android locale alone changes nothing. Instead, when the user picks a
 * language, every fixed UI text (collected at build time into
 * assets/i18n/ui_strings.json) is translated once with Gemini, cached on the
 * phone, and shown by [Text] below. Chat messages, names and other user content
 * are never in that list, so they are never translated or sent anywhere.
 */
object UiTranslator {
    private const val TAG = "UiTranslator"
    private const val PREFS = "ui_i18n_cache_v1"
    private const val CHUNK = 90

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _dict = MutableStateFlow<Map<String, String>>(emptyMap())
    val dictionary: StateFlow<Map<String, String>> = _dict.asStateFlow()

    /** 0..100 while a language is being prepared, -1 when idle. */
    private val _progress = MutableStateFlow(-1)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    @Volatile private var language: String? = null
    @Volatile private var whitelist: List<String>? = null
    private var job: Job? = null

    private fun loadWhitelist(context: Context): List<String> = whitelist ?: runCatching {
        val raw = context.assets.open("i18n/ui_strings.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrElse { emptyList() }.also { whitelist = it }

    /** [code] = the language the user explicitly chose, or null for the app's original texts. */
    @Synchronized
    fun setLanguage(context: Context, code: String?) {
        val app = context.applicationContext
        if (code == language) return
        language = code
        job?.cancel()
        _progress.value = -1
        if (code.isNullOrBlank()) {
            _dict.value = emptyMap()
            return
        }
        job = scope.launch {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = runCatching {
                val o = JSONObject(prefs.getString(code, null) ?: "{}")
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrDefault(emptyMap())
            _dict.value = cached
            val missing = loadWhitelist(app).filter { it !in cached }
            if (missing.isEmpty()) return@launch
            val label = WorldLanguages.displayLabel(code)
            val ai = SairaAgentAi(app)
            val merged = java.util.concurrent.ConcurrentHashMap(cached)
            val chunks = missing.chunked(CHUNK)
            var done = 0
            _progress.value = 0
            val gate = Semaphore(3)
            chunks.map { chunk ->
                async {
                    gate.withPermit {
                        val out = translateChunk(ai, chunk, label, code)
                        merged.putAll(out)
                        synchronized(this@UiTranslator) {
                            done++
                            if (language == code) {
                                _dict.value = HashMap(merged)
                                _progress.value = (done * 100 / chunks.size).coerceAtMost(99)
                            }
                        }
                    }
                }
            }.awaitAll()
            if (language == code) {
                prefs.edit().putString(code, JSONObject(merged as Map<*, *>).toString()).apply()
                _progress.value = -1
            }
        }
    }

    private suspend fun translateChunk(ai: SairaAgentAi, chunk: List<String>, label: String, code: String): Map<String, String> {
        val input = JSONArray(chunk).toString()
        repeat(3) { attempt ->
            val result = runCatching {
                val raw = ai.generate(
                    system = "You translate the user-interface texts of a chat app called Chatbox (AI assistant: Saira). " +
                        "Translate every item into $label (language code $code). Texts may be Bengali, English or mixed. " +
                        "Use short, natural app wording a native speaker expects. Keep emojis, arrows, numbers, " +
                        "%s/%d/%.1f placeholders, punctuation, and the names Chatbox, Saira, Gmail, GitHub, Firebase, " +
                        "Google, WhatsApp, Messenger unchanged. If an item is already in $label, return it unchanged.",
                    history = emptyList(),
                    prompt = "Return ONLY a JSON object mapping each index to its translation, e.g. {\"0\": \"...\", \"1\": \"...\"}.\n" +
                        "Items:\n$input",
                    json = true,
                    maxTokens = 8192
                )
                val o = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
                chunk.indices.mapNotNull { i ->
                    o.optString(i.toString()).takeIf { it.isNotBlank() }?.let { chunk[i] to it }
                }.toMap()
            }
            result.onSuccess { if (it.size >= chunk.size / 2) return it }
            result.onFailure { Log.w(TAG, "chunk failed (attempt ${attempt + 1}): ${it.message}") }
            delay(4_000L * (attempt + 1))
        }
        return emptyMap()
    }

    fun isRtl(code: String?): Boolean =
        code?.substringBefore('-')?.lowercase() in setOf("ar", "ur", "fa", "he", "iw", "ps", "sd", "ug", "yi")
}

/** The active translation table (empty = show original texts). */
val LocalUiDictionary = compositionLocalOf<Map<String, String>> { emptyMap() }

/** Translated version of a fixed UI text, for places that don't use [Text] (titles, descriptions…). */
@Composable
fun tr(text: String): String = LocalUiDictionary.current[text.trim()] ?: text

/** Drop-in replacement for material3 Text that shows the user's chosen language. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

/** AnnotatedString overload (rich text is shown as-is). */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style
    )
}
