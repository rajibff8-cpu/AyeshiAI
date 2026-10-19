package com.ayeshiai.assistant.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

data class AppEntry(
    val key: String,
    val label: String,
    val aliases: List<String>,
    val packages: List<String> = emptyList()
)

/** Allowlist of apps the assistant may open. Nothing outside this list can be launched by name. */
object AppCatalog {
    val entries: List<AppEntry> = listOf(
        AppEntry("youtube", "YouTube", listOf("youtube", "you tube", "yt", "यूट्यूब"), listOf("com.google.android.youtube")),
        AppEntry(
            "youtube_music", "YouTube Music",
            listOf("youtube music", "yt music", "music", "म्यूजिक", "यूट्यूब म्यूजिक"),
            listOf("com.google.android.apps.youtube.music")
        ),
        AppEntry("chrome", "Chrome", listOf("chrome", "browser", "क्रोम"), listOf("com.android.chrome")),
        AppEntry("settings", "Settings", listOf("settings", "setting", "सेटिंग", "सेटिंग्स")),
        AppEntry("calculator", "Calculator", listOf("calculator", "calc", "कैलकुलेटर")),
        AppEntry("camera", "Camera", listOf("camera", "कैमरा")),
        AppEntry(
            "maps", "Google Maps",
            listOf("google maps", "maps", "map", "navigation", "मैप्स", "मैप"),
            listOf("com.google.android.apps.maps")
        ),
        AppEntry("whatsapp", "WhatsApp", listOf("whatsapp", "व्हाट्सएप"), listOf("com.whatsapp")),
        AppEntry("telegram", "Telegram", listOf("telegram", "टेलीग्राम"), listOf("org.telegram.messenger")),
        AppEntry("gmail", "Gmail", listOf("gmail", "mail"), listOf("com.google.android.gm")),
        AppEntry("termux", "Termux", listOf("termux", "टर्मक्स"), listOf("com.termux")),
        AppEntry("instagram", "Instagram", listOf("instagram", "इंस्टाग्राम"), listOf("com.instagram.android")),
        AppEntry("spotify", "Spotify", listOf("spotify", "स्पॉटिफाई"), listOf("com.spotify.music")),
        AppEntry("playstore", "Play Store", listOf("play store", "playstore", "प्ले स्टोर"), listOf("com.android.vending"))
    )

    private val aliasList: List<Pair<String, String>> =
        entries.flatMap { e -> e.aliases.map { it to e.key } }.sortedByDescending { it.first.length }

    fun byKey(key: String): AppEntry? = entries.firstOrNull { it.key == key }

    /** Finds the app mentioned in the text. Multi-word aliases match as substrings, single words as whole tokens. */
    fun findKey(lower: String, tokens: List<String>): String? {
        val set = tokens.toSet()
        for ((alias, key) in aliasList) {
            val hit = if (alias.contains(' ')) lower.contains(alias) else alias in set
            if (hit) return key
        }
        return null
    }
}

enum class LaunchResult { OPENED, NOT_INSTALLED, FAILED }

class AppLauncher(private val context: Context) {
    private val pm get() = context.packageManager

    private fun intentFor(key: String): Intent? = when (key) {
        "settings" -> Intent(Settings.ACTION_SETTINGS)
        "calculator" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        else -> AppCatalog.byKey(key)?.packages?.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    }

    /** System apps (settings, calculator, camera) are reported as available; launching is still verified on open. */
    fun isInstalled(key: String): Boolean = when (key) {
        "settings", "calculator", "camera" -> true
        else -> AppCatalog.byKey(key)?.packages?.any { pm.getLaunchIntentForPackage(it) != null } == true
    }

    fun open(key: String): LaunchResult {
        val intent = intentFor(key) ?: return LaunchResult.NOT_INSTALLED
        return start(intent)
    }

    /** Opens YouTube / YouTube Music search results. It cannot auto-play a specific song. */
    fun searchYouTube(query: String, music: Boolean): LaunchResult {
        val q = Uri.encode(query)
        val url = if (music) "https://music.youtube.com/search?q=$q" else "https://www.youtube.com/results?search_query=$q"
        val pkg = if (music) "com.google.android.apps.youtube.music" else "com.google.android.youtube"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (pm.getLaunchIntentForPackage(pkg) != null) intent.setPackage(pkg)
        return start(intent)
    }

    private fun start(intent: Intent): LaunchResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            LaunchResult.OPENED
        } catch (e: ActivityNotFoundException) {
            LaunchResult.NOT_INSTALLED
        } catch (e: SecurityException) {
            LaunchResult.FAILED
        }
    }
}
