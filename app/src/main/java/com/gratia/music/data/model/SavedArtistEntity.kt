package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A remote artist the listener has saved — Gratia's local "follow", with no
 * claim about any account on the remote service.
 *
 * The remote identifier is the primary key, and that is the whole point: two
 * artists called "Radiohead" — one local collection artist and one remote
 * channel — are different rows in different tables, and never merge just
 * because their names agree. [remoteId] is the identity; [name] and
 * [artworkUrl] are cached for offline rendering.
 */
@Entity(tableName = "saved_artists")
data class SavedArtistEntity(
    /** The remote channel / browse id — the artist's actual identity. */
    @PrimaryKey val remoteId: String,
    val name: String,
    val artworkUrl: String? = null,
    /** Which catalogue the id belongs to, e.g. "youtube_music". */
    val source: String = "youtube_music",
    val savedAt: Long = System.currentTimeMillis(),
)
