package com.nexora.docly.data

import android.content.Context

/** AI reply language — user select karta hai, SharedPreferences mein save hota hai. */
object AiLanguage {

    private const val PREFS = "docly_prefs"
    private const val KEY = "ai_language"

    val ALL = listOf(
        // English + India
        "English", "Hinglish", "Hindi", "Bengali", "Urdu", "Punjabi", "Marathi", "Gujarati",
        "Tamil", "Telugu", "Kannada", "Malayalam", "Odia", "Assamese", "Sanskrit",
        "Nepali", "Sinhala", "Dhivehi",
        // Europe
        "Spanish", "French", "German", "Italian", "Portuguese", "Dutch", "Russian",
        "Ukrainian", "Polish", "Czech", "Slovak", "Hungarian", "Romanian", "Bulgarian",
        "Greek", "Croatian", "Serbian", "Slovenian", "Albanian", "Bosnian", "Macedonian",
        "Lithuanian", "Latvian", "Estonian", "Finnish", "Swedish", "Danish", "Norwegian",
        "Icelandic", "Irish", "Welsh", "Catalan", "Basque", "Belarusian", "Armenian", "Georgian",
        // Middle East + Central Asia
        "Arabic", "Hebrew", "Persian (Farsi)", "Turkish", "Azerbaijani", "Kazakh",
        "Uzbek", "Mongolian", "Pashto", "Kurdish", "Tajik",
        // East & South-East Asia
        "Japanese", "Korean", "Chinese (Simplified)", "Chinese (Traditional)", "Cantonese",
        "Thai", "Vietnamese", "Indonesian", "Malay", "Filipino (Tagalog)", "Khmer", "Lao",
        "Burmese", "Tibetan", "Javanese", "Sundanese", "Tetum",
        // Africa
        "Swahili", "Amharic", "Hausa", "Yoruba", "Igbo", "Zulu", "Xhosa", "Afrikaans",
        "Somali", "Tigrinya", "Oromo", "Twi", "Shona", "Kinyarwanda", "Malagasy",
        "Wolof", "Bambara", "Fulah", "Lingala"
    )

    fun current(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, "English")?.takeIf { it in ALL } ?: "English"
    }

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
    }

    /** System-prompt rule: pure selected language, native script, koi mix nahi. */
    fun rule(language: String): String = when (language) {
        "Hinglish" -> buildString {
            append(" Respond ONLY in Hinglish (Roman Hindi). ")
            append("Write Hindi sentences using English/Latin letters only - for example 'yeh document ek letter hai'. ")
            append("Never use Devanagari script. ")
            append("Use natural spoken Hinglish, the way young Indians mix Hindi and English in chat (for example 'maine samajh liya', 'yeh important hai'). ")
            append("Keep it casual and friendly. ")
        }
        else -> buildString {
            append(" Respond ONLY in $language. ")
            append("Use the proper native script of $language (Hindi = Devanagari, Arabic = Arabic script, etc). ")
            append("Do NOT mix any other language. Do NOT use Roman transliteration of $language. ")
            append("Do NOT write Hinglish or Roman Hindi even if the input contains it. ")
            append("Keep technical terms natural in $language. ")
        }
    }
}