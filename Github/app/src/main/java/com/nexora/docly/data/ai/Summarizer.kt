package com.nexora.docly.data.ai

import com.nexora.docly.data.AiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Document to short English summary (AI),
 * and direct answers to the user's questions based on the summary.
 * Big documents are split into chunks: highlights first, then a merged summary.
 */
object Summarizer {

    private const val CHUNK_CHARS = 100_000

    private val SYSTEM = buildString {
        append("You are Docly, a document summarizer. Your task: read the document text and produce ONE short, clean summary. ")
        append("STRICT RULES: ")
        append("1) Whole summary must be under 100 words. ")
        append("2) No markdown or formatting symbols at all: no asterisks (**), no hash tags (#), no headings (###), ")
        append("   no dashes (-), no separators (---), no bullets, no bold, no emojis. Plain readable text only. ")
        append("3) Start with one sentence saying what this document is (offer letter, invoice, diary entry, notice, etc). ")
        append("4) Then 4 to 6 short bullet lines with only the most important facts: names, amounts, salary, dates, deadlines, terms. ")
        append("5) If something is risky or needs attention, end with one line starting with 'Note:'. ")
        append("6) Never invent facts. Never repeat yourself. Never include your own commentary. ")
    }

    private val CHUNK_SYSTEM = buildString {
        append("You are a summarizer. The user gives you part of a document. ")
        append("Reply with max 5 short bullet lines of key facts only. ")
        append("No markdown symbols, no asterisks, no hashes, no emojis. Plain text only. Under 60 words. ")
    }

    suspend fun summarize(fileName: String, text: String, language: String): String =
        withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) throw IllegalArgumentException("Empty text")

        val system = SYSTEM + AiLanguage.rule(language)
        val chunkSystem = CHUNK_SYSTEM + " " + AiLanguage.rule(language)

        if (clean.length <= CHUNK_CHARS) {
            MistralApi.chat(
                listOf(
                    ChatMessage("system", system),
                    ChatMessage("user", "Document file name: $fileName\n\nDocument text:\n$clean")
                ),
                maxTokens = 500
            )
        } else {
            val chunks = chunk(clean)
            val highlights = chunks.mapIndexed { index, c ->
                MistralApi.chat(
                    listOf(
                        ChatMessage("system", chunkSystem),
                        ChatMessage("user", "This is part ${index + 1} of ${chunks.size} of the document:\n\n$c")
                    ),
                    maxTokens = 300
                )
            }
            val merged = highlights.joinToString("\n")
            MistralApi.chat(
                listOf(
                    ChatMessage("system", system),
                    ChatMessage(
                        "user",
                        "Document file name: $fileName\n\nCombined highlights of all parts:\n$merged\n\n" +
                            "Now write the final short summary following your rules."
                    )
                ),
                maxTokens = 500
            )
        }
    }

    private val CASUAL_SYSTEM = buildString {
        append("You are Docly, the friendly assistant of a document summarizer app. ")
        append("The user just sent a short casual chat message (like 'ok', 'thanks', 'hmm', 'nice', 'acha'). ")
        append("Reply warmly with ONE short friendly line, at most 10 words, matching their tone. ")
        append("Never mention documents, summaries, files or analysis. Do not repeat anything. ")
        append("No markdown, no emojis, plain text only. ")
    }

    private val CHAT_SYSTEM = buildString {
        append("You are Docly, a document assistant. The user is asking questions about a document whose summary and extracted text are provided below. ")
        append("Answer each question DIRECTLY from that text, quoting exact values when present (names, dates, times, amounts, IDs, contact details). ")
        append("STRICT RULES: ")
        append("1) Never start with any intro or header sentence like 'This document is...', 'This is a...', 'Here is...', or a repetition of the document description. Begin with the answer itself. ")
        append("2) Never repeat the document description, the summary, or the opening line that starts with 'This document is...' - never echo it again in any reply. ")
        append("3) Answer in 2 to 4 short plain lines. ")
        append("4) NO formatting at all: no dashes (-) at the start of lines, no bullets, no asterisks, no numbers before lines, no headings, no emojis. Plain readable text only. ")
        append("5) If the user asks for 'more details' or 'special/additional details', list NEW facts that were NOT already in the summary. ")
        append("6) Do NOT repeat what you said in earlier replies. ")
        append("7) Only if the document genuinely has no trace of the answer, reply with one short line meaning 'This detail is not in the document.' ")
    }

    suspend fun ask(
        documentContext: String,
        history: List<ChatMessage>,
        language: String
    ): String =
        withContext(Dispatchers.IO) {
            val lastUser = history.lastOrNull()?.content?.trim().orEmpty()
            if (isCasual(lastUser)) {
                return@withContext MistralApi.chat(
                    listOf(
                        ChatMessage("system", CASUAL_SYSTEM + AiLanguage.rule(language)),
                        ChatMessage("user", lastUser)
                    ),
                    maxTokens = 60
                )
            }

            val messages = buildList {
                add(ChatMessage("system", CHAT_SYSTEM + AiLanguage.rule(language)))
                add(ChatMessage("user", "Document:\n$documentContext"))
                addAll(history.takeLast(6))
            }
            MistralApi.chat(messages, maxTokens = 400)
        }

    private fun isCasual(text: String): Boolean {
        val t = text.lowercase().trim().trimEnd('.', '!', ',', ' ')
        return t in CASUAL_WORDS || (t.length <= 3 && !t.contains('?'))
    }

    private val CASUAL_WORDS = setOf(
        "ok", "oki", "okay", "k", "kk", "kay", "cool", "nice", "good", "great",
        "awesome", "perfect", "sure", "yes", "yep", "yeah", "yup", "no", "nope",
        "done", "fine", "acha", "achha", "theek", "thik", "haan", "ha", "nahi",
        "thanks", "thank you", "thankyou", "thx", "ty", "tnx", "tq",
        "dhanyavaad", "dhanyavad", "shukriya", "shukria", "got it", "understood",
        "ok bro", "ok bhai", "okay bro", "nice one", "well done", "great work",
        "ok thanks", "thank u", "hmm", "hm", "oh", "ah", "woah", "meh", "sahi",
        "sahi hai", "wow", "nices", "nicee", "okkk", "okayy", "thik hai", "theek hai"
    )

    private fun chunk(text: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + CHUNK_CHARS, text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOf('\n', end)
                if (boundary > start + CHUNK_CHARS / 2) end = boundary
            }
            out += text.substring(start, end)
            start = end
        }
        return out
    }
}