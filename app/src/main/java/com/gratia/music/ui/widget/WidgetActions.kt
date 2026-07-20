package com.gratia.music.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.gratia.music.GratiaApp
import kotlinx.coroutines.delay

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        GratiaApp.instance.playerManager.togglePlay()
        delay(100) // Small delay to let the state propagate
        GratiaMusicWidget().updateAll(context)
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        GratiaApp.instance.playerManager.nextSong()
        delay(100)
        GratiaMusicWidget().updateAll(context)
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        GratiaApp.instance.playerManager.prevSong()
        delay(100)
        GratiaMusicWidget().updateAll(context)
    }
}
