package com.gratia.music.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Action to perform when the sleep timer ends.
 */
enum class SleepAction {
    PAUSE, STOP, FADE_OUT
}

/**
 * Manages the sleep timer, stopping or fading out playback after a duration.
 * Runs as a singleton in GratiaApp.
 */
class SleepTimerManager(private val playerManager: PlayerManager) {

    companion object {
        private const val TAG = "GratiaSleepTimer"
        private const val FADE_DURATION_MS = 10000L // 10 seconds fade out
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _action = MutableStateFlow(SleepAction.FADE_OUT)
    val action: StateFlow<SleepAction> = _action.asStateFlow()

    fun startTimer(minutes: Int, action: SleepAction) {
        val durationMs = minutes * 60 * 1000L
        if (durationMs <= 0) return

        stopTimer() // clear existing
        
        Log.d(TAG, "startTimer: ${minutes}m, action=$action")
        _durationMs.value = durationMs
        _remainingMs.value = durationMs
        _action.value = action
        _isActive.value = true

        timerJob = scope.launch {
            val startTime = System.currentTimeMillis()
            var remaining = durationMs
            
            while (isActive && remaining > 0) {
                // If fading out and we're in the fade window, handle volume
                if (_action.value == SleepAction.FADE_OUT && remaining <= FADE_DURATION_MS) {
                    val progress = remaining.toFloat() / FADE_DURATION_MS
                    // e.g. 10s remaining -> vol 1.0, 5s remaining -> vol 0.5, 0s -> vol 0.0
                    playerManager.setVolume(progress)
                }

                delay(1000)
                remaining = durationMs - (System.currentTimeMillis() - startTime)
                _remainingMs.value = remaining.coerceAtLeast(0)
            }

            // Timer complete
            executeAction()
        }
    }

    fun stopTimer() {
        if (!_isActive.value) return
        Log.d(TAG, "stopTimer")
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingMs.value = 0L
        _durationMs.value = 0L
        
        // Restore volume if we were fading out
        if (_action.value == SleepAction.FADE_OUT) {
            playerManager.setVolume(1.0f)
        }
    }

    private fun executeAction() {
        Log.d(TAG, "Timer ended, executing action: ${_action.value}")
        when (_action.value) {
            SleepAction.PAUSE -> playerManager.pause()
            SleepAction.STOP -> playerManager.pause() // TODO: fully stop
            SleepAction.FADE_OUT -> {
                playerManager.pause()
                // Restore volume for next time user plays
                playerManager.setVolume(1.0f)
            }
        }
        
        _isActive.value = false
        _remainingMs.value = 0L
        _durationMs.value = 0L
        timerJob = null
    }
}
