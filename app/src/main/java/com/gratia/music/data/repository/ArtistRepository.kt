package com.gratia.music.data.repository

import com.gratia.music.data.dao.ArtistDao
import com.gratia.music.data.model.ArtistEntity
import com.gratia.music.data.network.ArtistImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ArtistRepository(private val artistDao: ArtistDao) {

    /**
     * Gets an ArtistEntity as a Flow, which acts as the single source of truth.
     * If the artist does not exist, it creates a new entry and automatically fetches the default API image.
     */
    fun getArtistFlow(name: String): Flow<ArtistEntity?> = flow {
        if (name.isBlank() || name == "<unknown>") {
            emit(null)
            return@flow
        }

        var artist = artistDao.getArtistByName(name)
        if (artist == null) {
            // Create a stub immediately so UI has something
            artist = ArtistEntity(name = name)
            artistDao.insertArtist(artist)
            
            // Try to fetch default image in background
            val fetchedUrl = ArtistImageFetcher.getArtistPictureUrl(name)
            if (fetchedUrl != null) {
                artist = artist.copy(pictureUrl = fetchedUrl, updatedAt = System.currentTimeMillis())
                artistDao.updateArtist(artist)
            }
        }
        
        // Emit changes from the DB continuously
        emitAll(artistDao.getArtistByNameFlow(name))
    }
    
    /**
     * Set a user-selected custom image for the given artist name.
     */
    suspend fun updateCustomImage(name: String, localPath: String?) {
        withContext(Dispatchers.IO) {
            val artist = artistDao.getArtistByName(name)
            if (artist != null) {
                artistDao.updateArtist(artist.copy(localPicturePath = localPath, updatedAt = System.currentTimeMillis()))
            } else {
                artistDao.insertArtist(ArtistEntity(name = name, localPicturePath = localPath))
            }
        }
    }

    /**
     * Clears the custom image and re-fetches the default API image for the artist.
     */
    suspend fun resetToDefaultImage(name: String) {
        withContext(Dispatchers.IO) {
            var artist = artistDao.getArtistByName(name)
            
            val fetchedUrl = ArtistImageFetcher.getArtistPictureUrl(name)
            
            if (artist != null) {
                // Keep the same ID but clear the local picture path and update the URL
                val updated = artist.copy(
                    localPicturePath = null, 
                    pictureUrl = fetchedUrl, 
                    updatedAt = System.currentTimeMillis()
                )
                artistDao.updateArtist(updated)
            } else {
                artistDao.insertArtist(ArtistEntity(name = name, pictureUrl = fetchedUrl))
            }
        }
    }
}
