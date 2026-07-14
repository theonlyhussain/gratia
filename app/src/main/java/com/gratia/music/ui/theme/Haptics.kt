package com.gratia.music.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Gratia Design Language (GDL) - Haptics System
 * Provides semantic haptic feedback.
 */
@Immutable
class GratiaHaptics {
    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    
    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    
    fun medium(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    
    fun heavy(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    
    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    
    fun warning(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
    
    fun error(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

val LocalGratiaHaptics = staticCompositionLocalOf { GratiaHaptics() }
