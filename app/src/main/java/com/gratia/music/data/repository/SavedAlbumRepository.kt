package com.gratia.music.data.repository

import com.gratia.music.data.dao.SavedAlbumDao
import com.gratia.music.data.model.SavedAlbumEntity
import kotlinx.coroutines.flow.Flow

/**
 * Saved remote albums: the album-sized sibling of [SavedArtistRepository].
 *
 * Save and un-save are local and immediate. The Library → Albums list reads
 * from this, not from the scanned `albums` table: an album the listener bookmarked
 * on a remote artist page is a catalogue entry they chose to keep, while a local
 * album is a folder of files that happened to be indexed. Keeping them in
 * different tables is what stops "OK Computer" (streamed) and "OK Computer" (a
 * folder on the phone) from collapsing into one row that is wrong about both.
 */
class SavedAlbumRepository(private val dao: SavedAlbumDao) {

    val savedAlbums: Flow<List<SavedAlbumEntity>> = dao.getAllSavedAlbums()

    fun isSavedFlow(remoteId: String): Flow<Boolean> = dao.isSavedFlow(remoteId)

    suspend fun isSaved(remoteId: String): Boolean = dao.isSaved(remoteId)

    suspend fun save(
        remoteId: String,
        title: String,
        artistName: String?,
        artistId: String?,
        artworkUrl: String?,
        year: String?,
        source: String = "youtube_music",
    ) {
        dao.insertSavedAlbum(
            SavedAlbumEntity(
                remoteId = remoteId,
                title = title,
                artistName = artistName,
                artistId = artistId,
                artworkUrl = artworkUrl,
                year = year,
                source = source,
            )
        )
    }

    suspend fun unsave(remoteId: String) {
        dao.deleteSavedAlbum(remoteId)
    }

    suspend fun get(remoteId: String): SavedAlbumEntity? = dao.getSavedAlbum(remoteId)
}
