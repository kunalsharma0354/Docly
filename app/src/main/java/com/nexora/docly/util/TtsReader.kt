package com.nexora.docly.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/**
 * Read-aloud engine. Wraps Android TextToSpeech with a curated locale map for
 * every language Docly can produce, graceful fallback (region variant -> plain
 * language -> English) and chunking for long responses. Speaking state is a
 * Compose state so buttons can show Stop/Play live.
 */
object TtsReader {

    var speaking by mutableStateOf(false)
        private set

    /** Engine init (or first speech) in progress — buttons show a spinner. */
    var loading by mutableStateOf(false)
        private set

    /** Id of the button currently speaking/loading — only it shows active state. */
    var activeId by mutableStateOf<String?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var engineReady = false
    private var currentLanguage: String? = null
    private data class Pending(val text: String, val language: String, val id: String)
    private val pending = ArrayDeque<Pending>()

    private val MAX_UTTERANCE = 3800

    private fun l(vararg tags: String): List<Locale> = tags.map { Locale.forLanguageTag(it) }

    private val LOCALES: Map<String, List<Locale>> = mapOf(
        // South Asia
        "English" to l("en-US", "en-GB", "en-IN"),
        "Hinglish" to l("hi-IN", "hi", "en-IN"),
        "Hindi" to l("hi-IN", "hi"),
        "Bengali" to l("bn-IN", "bn-BD", "bn"),
        "Urdu" to l("ur-IN", "ur-PK", "ur"),
        "Punjabi" to l("pa-IN", "pa-PK", "pa"),
        "Marathi" to l("mr-IN", "mr"),
        "Gujarati" to l("gu-IN", "gu"),
        "Tamil" to l("ta-IN", "ta-LK", "ta"),
        "Telugu" to l("te-IN", "te"),
        "Kannada" to l("kn-IN", "kn"),
        "Malayalam" to l("ml-IN", "ml"),
        "Odia" to l("or-IN", "or"),
        "Assamese" to l("as-IN", "as"),
        "Sanskrit" to l("sa-IN", "sa"),
        "Nepali" to l("ne-NP", "ne-IN", "ne"),
        "Sinhala" to l("si-LK", "si"),
        "Dhivehi" to l("dv-MV", "dv"),
        // Europe
        "Spanish" to l("es-ES", "es-MX", "es-US"),
        "French" to l("fr-FR", "fr-CA"),
        "German" to l("de-DE"),
        "Italian" to l("it-IT"),
        "Portuguese" to l("pt-BR", "pt-PT"),
        "Dutch" to l("nl-NL"),
        "Russian" to l("ru-RU"),
        "Ukrainian" to l("uk-UA"),
        "Polish" to l("pl-PL"),
        "Czech" to l("cs-CZ"),
        "Slovak" to l("sk-SK"),
        "Hungarian" to l("hu-HU"),
        "Romanian" to l("ro-RO"),
        "Bulgarian" to l("bg-BG"),
        "Greek" to l("el-GR"),
        "Croatian" to l("hr-HR"),
        "Serbian" to l("sr-RS", "sr-Latn-RS"),
        "Slovenian" to l("sl-SI"),
        "Albanian" to l("sq-AL"),
        "Bosnian" to l("bs-BA"),
        "Macedonian" to l("mk-MK"),
        "Lithuanian" to l("lt-LT"),
        "Latvian" to l("lv-LV"),
        "Estonian" to l("et-EE"),
        "Finnish" to l("fi-FI"),
        "Swedish" to l("sv-SE"),
        "Danish" to l("da-DK"),
        "Norwegian" to l("nb-NO", "no-NO"),
        "Icelandic" to l("is-IS"),
        "Irish" to l("ga-IE"),
        "Welsh" to l("cy-GB"),
        "Catalan" to l("ca-ES"),
        "Basque" to l("eu-ES"),
        "Belarusian" to l("be-BY"),
        "Armenian" to l("hy-AM"),
        "Georgian" to l("ka-GE"),
        // Middle East + Central Asia
        "Arabic" to l("ar-SA", "ar-EG"),
        "Hebrew" to l("he-IL"),
        "Persian (Farsi)" to l("fa-IR"),
        "Turkish" to l("tr-TR"),
        "Azerbaijani" to l("az-AZ"),
        "Kazakh" to l("kk-KZ"),
        "Uzbek" to l("uz-UZ"),
        "Mongolian" to l("mn-MN"),
        "Pashto" to l("ps-AF"),
        "Kurdish" to l("ku-TR", "ku-IQ", "ku"),
        "Tajik" to l("tg-TJ"),
        // East & South-East Asia
        "Japanese" to l("ja-JP"),
        "Korean" to l("ko-KR"),
        "Chinese (Simplified)" to l("zh-CN"),
        "Chinese (Traditional)" to l("zh-TW"),
        "Cantonese" to l("yue-HK", "zh-HK"),
        "Thai" to l("th-TH"),
        "Vietnamese" to l("vi-VN"),
        "Indonesian" to l("id-ID"),
        "Malay" to l("ms-MY"),
        "Filipino (Tagalog)" to l("fil-PH", "tl-PH"),
        "Khmer" to l("km-KH"),
        "Lao" to l("lo-LA"),
        "Burmese" to l("my-MM"),
        "Tibetan" to l("bo-IN", "bo-CN"),
        "Javanese" to l("jv-ID"),
        "Sundanese" to l("su-ID"),
        "Tetum" to l("tet-TL"),
        // Africa
        "Swahili" to l("sw-KE"),
        "Amharic" to l("am-ET"),
        "Hausa" to l("ha-NG", "ha-NE"),
        "Yoruba" to l("yo-NG"),
        "Igbo" to l("ig-NG"),
        "Zulu" to l("zu-ZA"),
        "Xhosa" to l("xh-ZA"),
        "Afrikaans" to l("af-ZA"),
        "Somali" to l("so-SO"),
        "Tigrinya" to l("ti-ET", "ti-ER"),
        "Oromo" to l("om-ET"),
        "Twi" to l("tw-GH"),
        "Shona" to l("sn-ZW"),
        "Kinyarwanda" to l("rw-RW"),
        "Malagasy" to l("mg-MG"),
        "Wolof" to l("wo-SN"),
        "Bambara" to l("bm-ML"),
        "Fulah" to l("ff-SN", "ff-Latn-SN"),
        "Lingala" to l("ln-CD")
    )

    fun speak(context: Context, text: String, language: String, id: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (tts == null) {
            loading = true
            activeId = id
            tts = TextToSpeech(context.applicationContext) { status ->
                engineReady = status == TextToSpeech.SUCCESS
                loading = false
                if (engineReady) {
                    pending.forEach { (t, l, i) -> doSpeak(t, l, i) }
                    pending.clear()
                } else {
                    tts?.shutdown()
                    tts = null
                    activeId = null
                }
            }
        }
        if (!engineReady) {
            pending.clear()
            pending.add(Pending(clean, language, id))
            loading = true
            activeId = id
            return
        }
        doSpeak(clean, language, id)
    }

    fun stop() {
        tts?.stop()
        pending.clear()
        speaking = false
        loading = false
        activeId = null
        currentLanguage = null
    }

    private fun doSpeak(text: String, language: String, id: String) {
        tts?.stop()
        val engine = tts ?: return

        val locales = LOCALES[language] ?: emptyList()
        val locale = locales.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: Locale.US
        engine.language = locale
        currentLanguage = language

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                speaking = false
                activeId = null
                currentLanguage = null
            }
            override fun onError(utteranceId: String?) {
                speaking = false
                activeId = null
                currentLanguage = null
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                speaking = false
                activeId = null
                currentLanguage = null
            }
        })

        val chunks = chunkText(text)
        chunks.forEachIndexed { index, part ->
            val utteranceId = UUID.randomUUID().toString()
            if (index == 0) {
                engine.speak(part, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                engine.speak(part, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }
        }
        speaking = true
        activeId = id
    }

    private fun chunkText(text: String): List<String> {
        if (text.length <= MAX_UTTERANCE) return listOf(text)
        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_UTTERANCE, text.length)
            if (end < text.length) {
                var boundary = text.lastIndexOf('\n', end)
                if (boundary > start + MAX_UTTERANCE / 2) end = boundary + 1
                else {
                    boundary = text.lastIndexOf(". ", end)
                    if (boundary > start + MAX_UTTERANCE / 2) end = boundary + 2
                }
            }
            out += text.substring(start, end).trim()
            start = end
        }
        return out.filter { it.isNotEmpty() }
    }
}
