package com.ayeshiai.assistant.commands

import java.util.Locale

sealed class Command {
    data class OpenApp(val appKey: String) : Command()
    data class YouTubeSearch(val query: String, val music: Boolean?) : Command()
    data class Remember(val text: String) : Command()
    object None : Command()
}

/**
 * Local rule-based router (Hinglish / Hindi / English). Only short, command-like sentences are
 * treated as device actions; everything else goes to Gemini as normal chat.
 * Gemini output is never executed as a command.
 */
object CommandRouter {
    private val punct = charArrayOf('.', ',', '!', '?', ';', ':', '"', '\'', '।', '(', ')')

    private val rememberTriggers = listOf(
        "yaad rakho", "yaad rakhna", "yaad rakh", "remember that", "remember this",
        "याद रखो", "याद रखना", "याद रख"
    )
    private val ytTokens = setOf("youtube", "yt", "यूट्यूब")
    private val searchVerbs = setOf(
        "search", "khoj", "khojo", "dhundo", "dhoondo", "play", "chala", "chalao", "chalado",
        "laga", "lagao", "gana", "gaana", "gane", "song", "songs",
        "सर्च", "खोजो", "ढूंढो", "प्ले", "चला", "चलाओ", "गाना", "गाने", "सुनाओ"
    )
    private val openVerbs = setOf(
        "open", "kholo", "khol", "kholna", "launch", "start", "chalu", "chalao", "chala", "khulao",
        "ओपन", "खोलो", "खोल", "चालू", "चलाओ", "चला"
    )
    private val stopWords = setOf(
        "please", "plz", "par", "pe", "mein", "me", "ko", "karo", "kar", "do", "dena", "de",
        "chala", "chalao", "chalado", "play", "lagao", "laga", "gana", "gane", "gaana",
        "ka", "ke", "ki", "mera", "mere", "meri", "open", "kholo", "search", "khojo", "khoj",
        "dhundo", "dhoondo", "aur", "and", "youtube", "yt", "music", "you", "tube",
        "for", "on", "the", "a", "na", "zara", "ek", "koi", "sunao",
        "पर", "पे", "में", "को", "करो", "कर", "दो", "दे", "चला", "चलाओ", "प्ले", "गाना", "गाने",
        "का", "के", "की", "मेरा", "मेरे", "मेरी", "सर्च", "खोजो", "ढूंढो", "यूट्यूब", "म्यूजिक",
        "और", "से", "सुनाओ", "खोलो"
    )

    fun parse(input: String, assistantName: String): Command {
        val lower = input.lowercase(Locale.ROOT).trim()

        for (t in rememberTriggers) {
            val i = lower.indexOf(t)
            if (i in 0..30) {
                val start = minOf(i + t.length, input.length)
                val rest = input.substring(start).trim().trimStart(':', ',', '-', ' ').trim()
                return if (rest.length >= 3) Command.Remember(rest) else Command.None
            }
        }

        val tokens = lower.split(Regex("\\s+")).map { it.trim(*punct) }.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 12) return Command.None
        val nameTokens = setOf(assistantName.lowercase(Locale.ROOT), "ayeshi", "ayesha", "आयेशी", "आयशी")

        val hasYt = tokens.any { it in ytTokens } || lower.contains("you tube")
        val isMusic = lower.contains("music") || lower.contains("म्यूजिक")

        if (hasYt && tokens.any { it in searchVerbs }) {
            val q = tokens.filter { it !in stopWords && it !in nameTokens }.joinToString(" ")
            return Command.YouTubeSearch(q, if (isMusic) true else null)
        }

        if (tokens.any { it in openVerbs }) {
            AppCatalog.findKey(lower, tokens)?.let { return Command.OpenApp(it) }
        }
        return Command.None
    }
}
