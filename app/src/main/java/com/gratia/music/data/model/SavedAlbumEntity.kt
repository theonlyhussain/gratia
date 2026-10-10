package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A remote album the listener has saved — the album-sized sibling of
 * [SavedArtistEntity], and deliberately its own table.
 *
 * The remote browse/album id is the primary key. A local album called
 * "OK Computer" scanned off the phone and the remote "OK Computer" are
 * different rows in different tables and never merge, because they are not the
 * same object: one is a folder of files, the other is a catalogue entry that
 * can be streamed. [title], [artistName] and [artworkUrl] are cached so the
 * Library and offline surfaces can render the row without a network call.
 */
@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    /** The remote album / browse id — the album's actual identity. */
    @PrimaryKey val remoteId: String,
    val title: String,
    val artistName: String? = null,
    /** The album artist's remote id, when the catalogue gave us one. */
    val artistId: String? = null,
    val artworkUrl: String? = null,
    val year: String? = null,
    /** Which catalogue the id belongs to, e.g. "youtube_music". */
    val source: String = "youtube_music",
    val savedAt: Long = System.currentTimeMillis(),
)
