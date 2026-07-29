package com.gratia.music.player

import android.util.Log

enum class QueueAction {
    MAINTAIN_VIBE,
    PENALIZE_VIBE,
    RADICAL_SHIFT,  // Triggered when user is frustrated
    WILDCARD_TRACK  // Triggered for dopamine discovery
}

class RetentionManager {
    private var rapidSkipCount = 0
    private var tracksPlayedInSession = 0
    
    // Configurable retention hooks
    private val RAPID_SKIP_THRESHOLD = 4
    private val WILDCARD_INTERVAL = 5 // Play a completely different vibe every 5th track

    companion object {
        private const val TAG = "RetentionManager"
    }

    fun processTrackEnd(listenDurationMs: Long, totalDurationMs: Long): QueueAction {
        tracksPlayedInSession++
        val listenPercentage = if (totalDurationMs > 0) {
            listenDurationMs.toFloat() / totalDurationMs
        } else {
            0f
        }

        Log.d(TAG, "processTrackEnd: listened $listenDurationMs of $totalDurationMs (${(listenPercentage * 100).toInt()}%)")

        // 1. The "Slot Machine" Hook
        if (tracksPlayedInSession % WILDCARD_INTERVAL == 0) {
            Log.d(TAG, "Action: WILDCARD_TRACK (Slot Machine Hook)")
            return QueueAction.WILDCARD_TRACK
        }

        // 2. Decode the Skip Intent
        return when {
            // Early Skip (Vibe Reject)
            listenDurationMs < 30_000 -> { 
                rapidSkipCount++
                Log.d(TAG, "Early skip detected. rapidSkipCount = $rapidSkipCount")
                
                // If they are skipping everything, they are frustrated. Pivot entirely.
                if (rapidSkipCount >= RAPID_SKIP_THRESHOLD) {
                    rapidSkipCount = 0 // Reset patience meter
                    Log.d(TAG, "Action: RADICAL_SHIFT (User frustrated)")
                    QueueAction.RADICAL_SHIFT
                } else {
                    Log.d(TAG, "Action: PENALIZE_VIBE")
                    QueueAction.PENALIZE_VIBE
                }
            }
            
            // Late Skip (Loved it, but done with it)
            listenPercentage > 0.85f -> {
                rapidSkipCount = 0
                Log.d(TAG, "Action: MAINTAIN_VIBE (Late skip / Completed)")
                QueueAction.MAINTAIN_VIBE
            }
            
            // Middle Skip (Got bored halfway)
            else -> {
                rapidSkipCount = 0
                Log.d(TAG, "Action: PENALIZE_VIBE (Mid skip)")
                QueueAction.PENALIZE_VIBE
            }
        }
    }
    
    fun resetSession() {
        rapidSkipCount = 0
        tracksPlayedInSession = 0
    }
}
