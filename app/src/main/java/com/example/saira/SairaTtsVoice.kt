package com.example.saira

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Picks a female voice for Saira from whatever TTS engine the phone has.
 * Android exposes no "gender" field, so this uses the naming schemes of the common engines:
 *  - Google: bn-bd-x-ban (f), bn-in-x-bin / bnf (f), bnm / bnx (m); en-us-x-tpf/sfg/iob/iog/tpc (f), iol/iom/tpd (m)…
 *  - Samsung: …-SMTf00 (f) / …-SMTm00 (m); others often put "female" in the name or features.
 * Local (offline) voices are preferred — they start speaking instantly.
 */
object SairaTtsVoice {
    private val FEMALE_HINTS = listOf(
        "female", "smtf", "-x-ban", "-x-bin", "-x-bnf", "-x-tpf", "-x-sfg", "-x-iob", "-x-iog", "-x-tpc",
        "-x-ena", "-x-ahp", "-x-cxx", "-x-gba", "-x-fis", "-x-gbc", "-x-gbg", "-x-hia", "-x-hic", "#female"
    )
    private val MALE_HINTS = listOf(
        "#male", "-male", "_male", "smtm", "-x-bnm", "-x-bnx", "-x-iol", "-x-iom", "-x-tpd", "-x-end",
        "-x-enc", "-x-gbb", "-x-gbd", "-x-rjs", "-x-hie", "-x-hid"
    )

    /** 2 = known female, 1 = unknown, 0 = known male. */
    fun femaleScore(v: Voice): Int {
        val name = v.name.lowercase(Locale.ROOT)
        val features = v.features.orEmpty().joinToString(" ").lowercase(Locale.ROOT)
        if (FEMALE_HINTS.any { name.contains(it) } || features.contains("female")) return 2
        if (MALE_HINTS.any { name.contains(it) } || Regex("(^|[^e])male").containsMatchIn(features)) return 0
        return 1
    }

    /**
     * Applies the best female voice for Bengali or English. Returns true when a voice was applied
     * (false = no voice for that language; the caller should not read the text with a foreign voice).
     */
    fun apply(engine: TextToSpeech, bengali: Boolean): Boolean {
        val wanted = if (bengali) listOf("bn") else listOf("en")
        val voices = runCatching { engine.voices }.getOrNull().orEmpty().filter { v ->
            v.locale.language in wanted &&
                !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        val best = voices.maxWithOrNull(
            compareBy<Voice>(
                { femaleScore(it) },
                { if (it.isNetworkConnectionRequired) 0 else 1 },
                { if (it.locale.country == (if (bengali) "BD" else "US")) 1 else 0 },
                { it.quality },
                { -it.latency }
            )
        )
        val ok = if (best != null) {
            runCatching { engine.voice = best }.isSuccess
        } else {
            val loc = if (bengali) listOf(Locale("bn", "BD"), Locale("bn", "IN"), Locale("bn")) else listOf(Locale.US)
            loc.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                ?.let { engine.language = it; true } ?: false
        }
        // A touch higher and brisker; a bit more lift when we could not confirm a female voice.
        engine.setPitch(if (best != null && femaleScore(best) == 2) 1.04f else 1.18f)
        engine.setSpeechRate(1.08f)
        return ok
    }
}
