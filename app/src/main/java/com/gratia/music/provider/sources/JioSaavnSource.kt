/*
 * Copyright (C) 2026 Kushagra Singh / Gratia contributors
 * Modified for Gratia by Hussain / Gratia contributors, 2026
 * Licensed under the GNU General Public License v3.0
 *
 * Source: Gratia data/jiosaavn/JioSaavnService.kt, data/sources/JioSaavnSource.kt.
 * https://github.com/kushagrasinghx/Gratia (GPLv3)
 *
 * Modifications for Gratia: the original TrackLog -> android.util.Log under
 * Gratia's tags; the source implements Gratia's [MusicSource] against
 * [RemoteTrack]; the conditional-320 rule and the real-bitrate reporting are
 * carried over exactly. Ktor client config unchanged.
 */
package com.gratia.music.provider.sources

import android.util.Base64
import android.util.Log
import com.gratia.music.provider.MusicProviderType
import com.gratia.music.provider.RemoteAlbumRef
import com.gratia.music.provider.RemoteArtistRef
import com.gratia.music.provider.RemoteTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Serializable
data class RawArtistMapItem(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class RawArtistMap(
    @SerialName("primary_artists") val primaryArtists: List<RawArtistMapItem> = emptyList(),
)

@Serializable
data class RawMoreInfo(
    val album_id: String = "",
    val album: String = "",
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String = "",
    val duration: String = "",
    /**
     * Whether a 320kbps rendition exists, as `"true"`/`"false"`.
     *
     * The catalogue states this per track and it is frequently false. Asking
     * the CDN for `_320` anyway does not produce one.
     */
    @SerialName("320kbps") val has320: String = "",
    val artistMap: RawArtistMap = RawArtistMap(),
) {
    val supports320: Boolean get() = has320.equals("true", ignoreCase = true)
}

/** A decoded CDN URL and the bitrate it will really deliver. */
data class SaavnStream(val url: String, val kbps: Int?)

@Serializable
data class RawSongItem(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    /** JioSaavn sends this as the string `"1"` or `"0"`. */
    @SerialName("explicit_content") val explicitContent: String = "",
    @SerialName("more_info") val moreInfo: RawMoreInfo = RawMoreInfo()
) {
    val isExplicit: Boolean
        get() = explicitContent == "1" || explicitContent.equals("true", ignoreCase = true)
}

@Serializable
data class RawSearchResponse(
    val results: List<RawSongItem> = emptyList()
)

/**
 * Put uncensored catalogue rows ahead of their clean duplicates.
 *
 * Top-level so the pure ordering rule remains unit-testable without
 * initialising Android's Base64-backed service singleton on the JVM.
 */
internal fun prioritizeExplicit(songs: List<RawSongItem>): List<RawSongItem> =
    songs.sortedByDescending { it.isExplicit }

object JioSaavnService {
    private const val TAG = "GratiaSaavn"

    // https://www.jiosaavn.com/api.php
    private val BASE_URL = String(
        Base64.decode("aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tL2FwaS5waHA=", Base64.DEFAULT),
        Charsets.UTF_8,
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 6_000
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 6_000
            }
            defaultRequest {
                url(BASE_URL)
                headers.append(HttpHeaders.Accept, "application/json")
                headers.append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                )
                headers.append("X-Forwarded-For", "49.36.0.1")
                headers.append("X-Real-IP", "49.36.0.1")
                headers.append("Accept-Language", "en-IN,en;q=0.9")
                headers.append(HttpHeaders.Cookie, "explicit_content=1")
            }
            expectSuccess = false
        }
    }

    private fun decryptUrl(encryptedUrl: String): String {
        if (encryptedUrl.isBlank()) return ""
        return try {
            val key = "38346591" // DES 8-byte key
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.w(TAG, "JioSaavn URL decryption failed: ${e.message}")
            ""
        }
    }

    /**
     * The best CDN URL this track really has, and the bitrate it will deliver.
     *
     * The rewrite to `_320` is conditional on [RawMoreInfo.supports320] rather
     * than applied to everything. Rewriting unconditionally is not an upgrade —
     * the CDN has no 320 rendition to serve for a track that hasn't got one —
     * and the old code did it anyway *and* then reported a flat 320 upstream.
     * A 96kbps stream advertised as 320 clears the swap test against YouTube's
     * 160kbps Opus, so the listener was swapped down to a third of the bitrate
     * on a line that claimed twice YouTube's.
     */
    private fun bestStream(encryptedUrl: String, supports320: Boolean): SaavnStream? {
        val decryptedUrl = decryptUrl(encryptedUrl)
        if (decryptedUrl.isBlank()) return null

        val suffix = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$").find(decryptedUrl)
            // No recognisable rung in the name, so there is nothing to rewrite
            // and nothing to claim: the bitrate goes up as unknown rather than
            // as a guess.
            ?: return SaavnStream(decryptedUrl, if (supports320) 320 else null)

        val offered = suffix.groupValues[1].toIntOrNull()
        val extension = suffix.groupValues[2]
        return if (supports320) {
            SaavnStream(decryptedUrl.replaceRange(suffix.range, "_320.$extension"), 320)
        } else {
            SaavnStream(decryptedUrl, offered)
        }
    }

    suspend fun searchSongs(query: String): List<RawSongItem> = runCatching {
        val response = client.get("") {
            parameter("__call", "search.getResults")
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("api_version", "4")
            parameter("ctx", "android")
            parameter("q", query)
            parameter("p", "1")
            parameter("n", "10")
        }

        if (response.status != HttpStatusCode.OK) {
            Log.w(TAG, "Saavn search failed: HTTP ${response.status.value}")
            return@runCatching emptyList()
        }

        val body = json.decodeFromString<RawSearchResponse>(response.bodyAsText())
        body.results
    }.getOrElse {
        Log.w(TAG, "Saavn search error: ${it.message}")
        emptyList()
    }

    suspend fun getStreamUrl(saavnSongId: String): SaavnStream? {
        val result = runCatching {
            val response = client.get("") {
                parameter("__call", "song.getDetails")
                parameter("_format", "json")
                parameter("_marker", "0")
                parameter("api_version", "4")
                parameter("ctx", "android")
                parameter("pids", saavnSongId)
            }

            if (response.status != HttpStatusCode.OK) {
                Log.w(TAG, "Saavn getDetails failed: HTTP ${response.status.value}")
                return@runCatching null
            }

            // `song.getDetails` does not answer with the `{"songs":[…]}` envelope
            // `search.getResults` uses. It answers with a map keyed by the id
            // that was asked for. Both shapes are read here so neither endpoint
            // changing its mind breaks the other.
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@runCatching null
            val songElement = (root["songs"] as? JsonArray)?.firstOrNull()
                ?: root.values.firstOrNull { it is JsonObject }
                ?: run {
                    Log.w(TAG, "Saavn getDetails held no song for $saavnSongId")
                    return@runCatching null
                }
            val rawSong = json.decodeFromJsonElement(RawSongItem.serializer(), songElement)

            bestStream(rawSong.moreInfo.encryptedMediaUrl, rawSong.moreInfo.supports320)
        }
        return result.onFailure { Log.w(TAG, "Saavn getDetails error: ${it.message}") }.getOrNull()
    }
}

/**
 * Gratia's JioSaavn source: a catalogue asked for a better rendition of a
 * recording YouTube is already playing.
 *
 * JioSaavn is not an equaliser and not an enhancer. Its value is that it
 * sometimes holds a genuinely better-encoded rendition of the same recording —
 * notably 320kbps AAC — and everything here is about finding that honestly:
 * real metadata, real bitrate, and refusals where the rendition is not worth
 * taking.
 */
class JioSaavnSource : MusicSource {

    override val configId: String get() = "jiosaavn"
    override val kind: SourceKind get() = SourceKind.JIOSAAVN
    override val displayName: String get() = SourceKind.JIOSAAVN.label

    /** Always Ok — the API endpoints don't need authentication to search. */
    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<RemoteTrack> {
        Log.d(TAG, "▶ JioSaavn searchSongs() query=\"$query\" limit=$limit")
        val results = prioritizeExplicit(JioSaavnService.searchSongs(query))
        Log.d(
            TAG,
            "  ✓ JioSaavn returned ${results.size} tracks" + results.take(5)
                .joinToString(prefix = ": ", separator = "; ") {
                    "'${it.title}' ${it.moreInfo.duration}s id=${it.id} " +
                        "album='${it.moreInfo.album}' explicit=${it.explicitContent}"
                }.takeIf { results.isNotEmpty() }.orEmpty(),
        )
        return results.take(limit).map { raw ->
            val primaryArtists = raw.moreInfo.artistMap.primaryArtists.joinToString(", ") { it.name }
            val artistName = primaryArtists.ifBlank { "Unknown Artist" }

            // Generate higher quality thumbnail link (e.g. 500x500)
            val thumbnail = raw.image
                .replace(Regex("150x150|50x50"), "500x500")
                .replace(Regex("^http://"), "https://")

            RemoteTrack(
                id = SourceTrackKey.of(configId, raw.id),
                provider = MusicProviderType.YOUTUBE_MUSIC, // played through the source layer, not YT
                title = raw.title,
                artists = listOf(RemoteArtistRef(id = null, name = artistName)),
                // Keep the release identity JioSaavn already gave us. Its
                // catalogue contains different recordings under the same title
                // and artist, so dropping this left duration to choose between
                // them.
                album = RemoteAlbumRef(
                    id = raw.moreInfo.album_id.ifBlank { null },
                    name = raw.moreInfo.album,
                ),
                artworkUrl = thumbnail,
                durationText = raw.moreInfo.duration.toIntOrNull()?.let { seconds ->
                    val m = seconds / 60
                    val s = seconds % 60
                    String.format("%d:%02d", m, s)
                },
                videoId = SourceTrackKey.of(configId, raw.id),
                isExplicit = raw.isExplicit,
            )
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        Log.d(TAG, "▶ JioSaavn getStreamUrl() trackId=$trackId request=$request")
        val stream = JioSaavnService.getStreamUrl(trackId)
        if (stream == null || stream.url.isBlank()) {
            Log.w(TAG, "  ✗ JioSaavn had no stream URL for $trackId")
            return null
        }
        // A rendition this thin is worse than the YouTube stream it would be
        // replacing. Refused here rather than handed up and left to
        // [SourceResolver.worthSwapping], because a miss lets the resolver
        // step over this source, whereas a stream returned and then rejected
        // is simply the track not being upgraded at all.
        if (stream.kbps != null && stream.kbps <= MIN_USABLE_KBPS) {
            Log.w(TAG, "  ✗ JioSaavn only offered ${stream.kbps}kbps for $trackId; not worth playing")
            return null
        }
        Log.d(TAG, "  ✓ JioSaavn ${stream.kbps ?: "?"}kbps ${stream.url.take(96)}")
        return SourceStream(
            url = stream.url,
            // The rate the URL will really serve, not a flat 320 — see
            // [JioSaavnService.bestStream]. `mp4` is the container; the codec
            // inside is AAC, which the decoder reports for itself.
            format = StreamFormat(codec = "mp4", kbps = stream.kbps),
        )
    }

    private companion object {
        private const val TAG = "GratiaSaavn"

        /**
         * The lowest rendition worth taking over YouTube.
         *
         * JioSaavn files a track at 48, 96, 160 or 320. The first two are below
         * what YouTube already serves, so taking one is a downgrade dressed as
         * an upgrade.
         */
        const val MIN_USABLE_KBPS = 96
    }
}

