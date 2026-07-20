package com.gratia.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gratia.music.data.dao.SongDao
import com.gratia.music.data.dao.UserProfileDao
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.data.model.PlaylistSongCrossRef
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.UserProfileEntity
import com.gratia.music.data.model.ListeningEventEntity
import com.gratia.music.data.dao.ListeningEventDao
import com.gratia.music.data.dao.LyricsDao
import com.gratia.music.data.dao.ArtistDao
import com.gratia.music.data.dao.AlbumDao
import com.gratia.music.data.dao.ArtworkDao
import com.gratia.music.data.dao.SyncQueueDao
import com.gratia.music.data.model.LyricsEntity
import com.gratia.music.data.model.ArtistEntity
import com.gratia.music.data.model.AlbumEntity
import com.gratia.music.data.model.ArtworkEntity
import com.gratia.music.data.model.SyncQueueEntity

/**
 * Room migration from v1 to v2.
 * Adds cover art, lyrics mode, and audio quality fields to the songs table.
 * Preserves all existing data including songs, lyrics, favorites, and metadata.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Cover art fields
        db.execSQL("ALTER TABLE songs ADD COLUMN coverArtPath TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN coverSource TEXT DEFAULT NULL")

        // Lyrics mode fields
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsPlain TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsSynced TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN lyricsMode TEXT NOT NULL DEFAULT 'plain'")

        // Audio quality metadata fields
        db.execSQL("ALTER TABLE songs ADD COLUMN format TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN bitrate INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN sampleRate INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN fileSizeBytes INTEGER DEFAULT NULL")

        // Migrate existing lyrics data to lyricsPlain so it is not lost
        db.execSQL("UPDATE songs SET lyricsPlain = lyrics WHERE lyrics IS NOT NULL AND lyrics != ''")
    }
}

/**
 * Room migration from v2 to v3.
 * Adds user_profile table for local profile customization.
 * Safe additive migration — no existing data is touched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profile (
                id TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL DEFAULT 'Music Lover',
                avatarPath TEXT DEFAULT NULL,
                bannerPath TEXT DEFAULT NULL,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        // Insert default profile row
        db.execSQL("INSERT OR IGNORE INTO user_profile (id, displayName, updatedAt) VALUES ('default', 'Music Lover', 0)")
    }
}

/**
 * Room migration from v3 to v4.
 * Adds listening_events table for local Listening Calendar tracking.
 * Safe additive migration.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS listening_events (
                id TEXT NOT NULL PRIMARY KEY,
                songId TEXT NOT NULL,
                eventType TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                listenedSeconds INTEGER NOT NULL,
                source TEXT NOT NULL,
                completed INTEGER NOT NULL,
                skipped INTEGER NOT NULL
            )
        """)
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN skipCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN completedCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN totalListenTime INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT DEFAULT NULL,
                coverArtUri TEXT DEFAULT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS playlist_songs (
                playlistId TEXT NOT NULL,
                songId TEXT NOT NULL,
                addedAt INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(playlistId, songId)
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS collections (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT DEFAULT NULL,
                coverArtUri TEXT DEFAULT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS collection_songs (
                collectionId TEXT NOT NULL,
                songId TEXT NOT NULL,
                addedAt INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(collectionId, songId)
            )
        """)
    }
}

/**
 * Room migration from v5 to v6.
 * Creates the lyrics table and migrates data from songs table if present.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS lyrics (
                songId TEXT NOT NULL PRIMARY KEY,
                text TEXT NOT NULL,
                isSynced INTEGER NOT NULL,
                provider TEXT NOT NULL,
                offsetMs INTEGER NOT NULL,
                isManuallyEdited INTEGER NOT NULL,
                downloadDate INTEGER NOT NULL
            )
        """)
        
        // Migrate existing plain lyrics. We assume old lyrics are un-synced and provided locally.
        db.execSQL("""
            INSERT INTO lyrics (songId, text, isSynced, provider, offsetMs, isManuallyEdited, downloadDate)
            SELECT id, lyricsPlain, 0, 'legacy_migration', 0, 0, 0
            FROM songs
            WHERE lyricsPlain IS NOT NULL AND lyricsPlain != ''
        """)
        
        // Note: SQLite doesn't support DROP COLUMN easily before newer versions, 
        // so we just leave the old columns in the songs table but Room will ignore them 
        // because we removed them from the Entity.
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS artists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                pictureUrl TEXT DEFAULT NULL,
                localPicturePath TEXT DEFAULT NULL,
                pictureHash TEXT DEFAULT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS albums (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                artistId TEXT DEFAULT NULL,
                releaseDate TEXT DEFAULT NULL,
                coverUrl TEXT DEFAULT NULL,
                localCoverPath TEXT DEFAULT NULL,
                coverHash TEXT DEFAULT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS artwork_cache (
                id TEXT NOT NULL PRIMARY KEY,
                url TEXT NOT NULL,
                localPath TEXT NOT NULL,
                hash TEXT DEFAULT NULL,
                lastUpdated INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_queue (
                id TEXT NOT NULL PRIMARY KEY,
                songId TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'QUEUED',
                retryCount INTEGER NOT NULL DEFAULT 0,
                queuedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        // Add new columns to songs table
        db.execSQL("ALTER TABLE songs ADD COLUMN albumArtistId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN trackNumber INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN discNumber INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN releaseDate TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN isrc TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN bpm REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN composer TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN explicit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN popularity INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN label TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN copyright TEXT DEFAULT NULL")
        
        db.execSQL("ALTER TABLE songs ADD COLUMN lastMetadataSync INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN metadataSource TEXT NOT NULL DEFAULT 'local'")
    }
}

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        com.gratia.music.data.model.CollectionEntity::class,
        com.gratia.music.data.model.CollectionSongCrossRef::class,
        UserProfileEntity::class,
        ListeningEventEntity::class,
        LyricsEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        ArtworkEntity::class,
        SyncQueueEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class GratiaDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun playlistDao(): com.gratia.music.data.dao.PlaylistDao
    abstract fun collectionDao(): com.gratia.music.data.dao.CollectionDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: GratiaDatabase? = null

        fun getInstance(context: Context): GratiaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GratiaDatabase::class.java,
                    "gratia_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
