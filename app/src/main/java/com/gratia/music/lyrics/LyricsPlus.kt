package com.gratia.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

/**
 * Syllable-timed lyrics from LyricsPlus, the open backend behind the YouLy+
 * extension. It aggregates Apple Music, QQ Music and Musixmatch, and its v2
 * response is the finest-grained of the providers here — Apple's own syllable
 * splits, not just word boundaries.
 *
 * The catch is hosting: it runs on volunteer mirrors, and at any given moment
 * most of them are rate-limited, out of Vercel credit or simply gone. The
 * extension's answer, copied here, is to ask all of them at once and take the
 * first real answer. The winner is remembered so the next track goes straight
 * to a host that was up a minute ago instead of paying for the race again.
 */
object LyricsPlus : LyricsProvider {

    override val name = "LyricsPlus"

    private val MIRRORS = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )

    private val lastGood = AtomicReference<String?>(null)

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        videoId: String?
    ): LyricsResult? = coroutineScope {
        val hosts = lastGood.get()
            ?.let { listOf(it) + MIRRORS.filterNot { mirror -> mirror == it } }
            ?: MIRRORS

        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) { fetch(host, title, artist, durationMs ?: 0L, album) }
        }.toMutableList()

        // Take the first mirror to answer with something usable
        try {
            while (pending.isNotEmpty()) {
                val (host, body) = select {
                    pending.forEach { (host, job) -> job.onAwait { host to it } }
                }
                pending.removeAll { it.first == host }
                if (body != null) {
                    lastGood.set(host)
                    val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
                    val isSyllable = response?.lyrics?.any { !it.syllabus.isNullOrEmpty() } == true
                    
                    // Match confidence heuristic: 
                    // LyricsPlus is title+artist based. High confidence.
                    return@coroutineScope LyricsResult(
                        text = body,
                        syncLevel = if (isSyllable) SyncLevel.SYLLABLE else SyncLevel.LINE,
                        providerName = name,
                        matchConfidence = 85, // Reasonable default for T/A match
                        durationDifferenceMs = 0L // LyricsPlus doesn't return track duration to diff against
                    )
                }
            }
            null
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        host: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): String? = withContext(Dispatchers.IO) {
        val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("duration", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            ?: return@withContext null
        if (response.lyrics.isNullOrEmpty()) null else body
    }

    @Serializable
    internal data class Response(
        val type: String? = null,
        val lyrics: List<Line>? = null,
    )

    @Serializable
    internal data class Line(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        @SerialName("syllabus") val syllabus: List<Syllable>? = null,
    )

    @Serializable
    internal data class Syllable(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
    )
}
