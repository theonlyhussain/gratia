package com.gratia.music.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object LyricsDiagnosticManager {

    private const val TAG = "LyricsDiagnostic"

    suspend fun testAllProviders(title: String, artist: String, album: String?, durationMs: Long?, videoId: String?) = withContext(Dispatchers.IO) {
        val providers = listOf(
            BetterLyrics,
            LyricsPlus,
            SimpMusicLyrics,
            KuGou,
            LRCLIBProvider(),
            LyricallyProvider(),
            LyricsifyProvider(),
            YouTubeMusicLyricsProvider()
        )

        Log.d(TAG, "=========================================================")
        Log.d(TAG, "DIAGNOSTIC TEST FOR: $title - $artist")
        Log.d(TAG, "=========================================================")

        val results = providers.map { provider ->
            async {
                val start = System.currentTimeMillis()
                val result = try {
                    withTimeoutOrNull(10000) {
                        provider.fetchLyrics(title, artist, album, durationMs, videoId)
                    }
                } catch (e: Exception) {
                    null
                }
                val time = System.currentTimeMillis() - start
                
                if (result != null) {
                    val mode = LyricsModeDetector.detectMode(result.text)
                    val parsed = LyricsParser.parse(result.text)
                    
                    val syncType = when (parsed) {
                        is LyricsDocument.WordSynced -> "WordSynced"
                        is LyricsDocument.LineSynced -> "LineSynced"
                        is LyricsDocument.Plain -> "Plain"
                        null -> "Failed to Parse"
                    }
                    
                    val lineCount = when (parsed) {
                        is LyricsDocument.WordSynced -> parsed.lines.size
                        is LyricsDocument.LineSynced -> parsed.lines.size
                        else -> 0
                    }
                    val wordCount = when (parsed) {
                        is LyricsDocument.WordSynced -> parsed.lines.sumOf { it.words.size }
                        else -> 0
                    }
                    
                    Log.d(TAG, "[${provider.name}] (${time}ms) - Format: $mode - Sync: $syncType - Lines: $lineCount - Words: $wordCount")
                } else {
                    Log.d(TAG, "[${provider.name}] (${time}ms) - Failed or No Lyrics Found")
                }
            }
        }
        
        results.awaitAll()
        Log.d(TAG, "=========================================================")
    }
}
