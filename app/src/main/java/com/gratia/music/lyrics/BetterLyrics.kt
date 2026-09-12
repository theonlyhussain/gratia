package com.gratia.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Word-timed lyrics from BetterLyrics — the backend behind the YouTube Music
 * browser extension of the same name.
 *
 * One key-less call keyed on title, artist and duration, answering with Apple
 * Music's own TTML. That combination is why it leads the chain: no track-id
 * lookup, no token to scrape, no login, and the timing is per-syllable.
 *
 * Note this is the extension's original host. The project's newer Cloudflare
 * API puts the same endpoint behind a Turnstile challenge, which a native
 * client has no way to answer.
 */
object BetterLyrics : LyricsProvider {

    override val name = "BetterLyrics"

    private const val BASE = "https://lyrics-api.boidu.dev/getLyrics"

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        videoId: String?
    ): LyricsResult? = withContext(Dispatchers.IO) {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                val seconds = (durationMs ?: 0L) / 1000
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("al", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        val ttml = runCatching {
            (lyricsJson.parseToJsonElement(body) as? JsonObject)
                ?.get("ttml")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return@withContext null

        val lines = TtmlLyrics.parse(ttml).takeIf { it.isNotEmpty() } ?: return@withContext null
        
        val isSyllable = lines.any { it.isWordSynced }

        LyricsResult(
            text = ttml,
            syncLevel = if (isSyllable) SyncLevel.SYLLABLE else SyncLevel.LINE,
            providerName = name,
            matchConfidence = 90, // BetterLyrics considers duration internally
            durationDifferenceMs = 0L 
        )
    }
}
