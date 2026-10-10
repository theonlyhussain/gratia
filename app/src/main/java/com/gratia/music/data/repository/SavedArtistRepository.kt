package com.gratia.music.data.repository

import com.gratia.music.data.dao.SavedArtistDao
import com.gratia.music.data.model.SavedArtistEntity
import kotlinx.coroutines.flow.Flow

/**
 * Saved remote artists: Gratia's local follow list.
 *
 * Save and un-save are local, immediate operations — the button must never
 * wait on a network round trip to change state (spec 88). There is no
 * authenticated YouTube Music account layer in Gratia, so a save is exactly
 * what it claims to be: a local bookmark, presented as such.
 */
class SavedArtistRepository(private val dao: SavedArtistDao) {

    val savedArtists: Flow<List<SavedArtistEntity>> = dao.getAllSavedArtists()

    fun isSavedFlow(remoteId: String): Flow<Boolean> = dao.isSavedFlow(remoteId)

    suspend fun isSaved(remoteId: String): Boolean = dao.isSaved(remoteId)

    suspend fun save(remoteId: String, name: String, artworkUrl: String?, source: String = "youtube_music") {
        dao.insertSavedArtist(
            SavedArtistEntity(remoteId = remoteId, name = name, artworkUrl = artworkUrl, source = source)
        )
    }

    suspend fun unsave(remoteId: String) {
        dao.deleteSavedArtist(remoteId)
    }

    suspend fun get(remoteId: String): SavedArtistEntity? = dao.getSavedArtist(remoteId)
}
