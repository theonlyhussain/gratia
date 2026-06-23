package com.gratia.music.data.dao

import androidx.room.*
import com.gratia.music.data.model.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 'default'")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 'default'")
    suspend fun getProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET displayName = :name, updatedAt = :now WHERE id = 'default'")
    suspend fun updateDisplayName(name: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET avatarPath = :path, updatedAt = :now WHERE id = 'default'")
    suspend fun updateAvatar(path: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET bannerPath = :path, updatedAt = :now WHERE id = 'default'")
    suspend fun updateBanner(path: String?, now: Long = System.currentTimeMillis())
}
