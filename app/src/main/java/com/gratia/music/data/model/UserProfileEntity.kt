package com.gratia.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local user profile stored in Room.
 * Supports display name, avatar, and banner customization.
 * Single row with fixed id = "default".
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = "default",
    val displayName: String = "Music Lover",
    val avatarPath: String? = null,
    val bannerPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
