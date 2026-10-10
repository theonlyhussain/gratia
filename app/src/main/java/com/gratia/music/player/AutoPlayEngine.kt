package com.gratia.music.player

import android.util.Log
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.provider.RemoteTrackMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What one radio request came back with.
 *
 * [Tracks] with an empty list is a genuine answer — the catalogue had nothing
 * to add. [Failed] is no answer at all: the network, the parser or the remote
 * end refused, and the queue must say so rather than sit in silence that looks
 * identical to "nothing more to play". The two states drive different UI.
 */
sealed interface RadioOutcome {
    data class Tracks(val suggestions: List<SongEntity>) : RadioOutcome
    data object Failed : RadioOutcome
}

/**
 * Where AutoPlay's suggestions come from.
 *
 * Two sources, and only two. For a remote track the answer is the remote
 * catalogue's own watch queue — the radio mix YouTube Music would play after
 * that video, reached through `next`/`RDAMVM<videoId>`. That is a genuine
 * continuation: it is the same data the service itself would use, and it is
 * seeded by the track being listened to, not by its position in a list.
 *
 * For a local track there is no radio to ask, so the local generator below
 * stands in. It is deliberately *not* used to fill remote chains — a local file
 * cannot continue a radio, and mixing one in would end the chain instead of
 * extending it. See [RadioQueue.filterSuggestions], which enforces the same
 * rule from the other side.
 */
class AutoPlayEngine(private val repository: SongRepository) {

    companion object {
        private const val TAG = "AutoPlayEngine"
    }

    /**
     * The radio continuation for [seedVideoId], mapped into Gratia's unified
     * track model and filtered down to usable suggestions.
     *
     * Returns [RadioOutcome.Failed] on any network or parse failure; a
     * successful request with nothing left to suggest is
     * [RadioOutcome.Tracks] carrying an empty list.
     */
    suspend fun radioSuggestions(
        seedVideoId: String,
        knownIds: Set<String>,
    ): RadioOutcome = withContext(Dispatchers.IO) {
        Log.d(TAG, "AUTOPLAY_REQUEST seed=$seedVideoId")

        val mapped = try {
            GratiaApp.instance.providerManager.youtubeMusicProvider
                .getRelatedTracks(seedVideoId)
                .map { RemoteTrackMapper.toSongEntity(it) }
        } catch (e: Exception) {
            Log.w(TAG, "AUTOPLAY_ERROR seed=$seedVideoId stage=radio ${e.message}")
            return@withContext RadioOutcome.Failed
        }

        Log.d(TAG, "AUTOPLAY_RESULT_COUNT seed=$seedVideoId count=${mapped.size}")

        if (mapped.any { it.providerTrackId == seedVideoId }) {
            Log.d(TAG, "AUTOPLAY_FILTERED_SEED seed=$seedVideoId")
        }

        val filtered = RadioQueue.filterSuggestions(
            seedVideoId = seedVideoId,
            candidates = mapped,
            alreadyKnown = knownIds,
        )

        val duplicates = mapped.size - filtered.size
        Log.d(TAG, "AUTOPLAY_FILTERED_COUNT seed=$seedVideoId dropped=$duplicates kept=${filtered.size}")

        RadioOutcome.Tracks(filtered)
    }

    /**
     * The local stand-in: a track in the same spirit as the one that just
     * played, used only when the listener is on local music.
     */
    suspend fun localSuggestion(
        action: QueueAction,
        currentSong: SongEntity,
        knownIds: Set<String>,
    ): SongEntity? = withContext(Dispatchers.IO) {
        // The same candidate rule the remote path uses: never the song that is
        // already playing, never one already in the queue.
        val history = knownIds.toList()
        val MAX_ATTEMPTS = 5
        var candidate: SongEntity? = null

        for (i in 0 until MAX_ATTEMPTS) {
            candidate = when (action) {
                QueueAction.MAINTAIN_VIBE -> {
                    val genre = currentSong.genre
                    if (!genre.isNullOrBlank()) {
                        repository.getRandomSongByGenre(genre)
                    } else {
                        repository.getRandomSong()
                    }
                }
                QueueAction.PENALIZE_VIBE -> {
                    val genre = currentSong.genre
                    val artist = currentSong.artist
                    if (!genre.isNullOrBlank()) {
                        repository.getRandomSongByGenreExcludingArtist(genre, artist)
                    } else {
                        repository.getRandomSongExcludingArtist(artist)
                    }
                }
                QueueAction.RADICAL_SHIFT -> {
                    val genre = currentSong.genre
                    if (!genre.isNullOrBlank()) {
                        repository.getRandomSongExcludingGenre(genre)
                    } else {
                        repository.getRandomSong()
                    }
                }
                QueueAction.WILDCARD_TRACK -> repository.getRandomSong()
            }

            if (candidate != null && candidate.id !in history && candidate.id != currentSong.id) {
                break
            }
        }

        val result = candidate?.takeIf { it.id !in history && it.id != currentSong.id }
        if (result == null) {
            Log.d(TAG, "AUTOPLAY_EXHAUSTED source=local action=$action")
        }
        result
    }
}
