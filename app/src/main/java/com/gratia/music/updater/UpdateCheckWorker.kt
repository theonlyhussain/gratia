package com.gratia.music.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * The retired periodic update check.
 *
 * Gratia used to enqueue this work once every 24 hours so a release could find
 * the user on its own. That is no longer how updates work: a check only happens
 * when the user asks for one, from Settings. The class survives only so that
 * installs which already have the work on disk can have it cancelled — and so
 * that a queued run that fires before the cancellation lands does nothing.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Never touch the network from here again. Failure is deliberate: it is
        // how a periodic request stops rather than being rescheduled forever.
        return Result.failure()
    }

    companion object {
        const val WORK_NAME = "UpdateCheckWorker"

        /**
         * Removes the periodic check from an install that predates this change.
         * A no-op when nothing was ever scheduled.
         */
        fun cancelLegacySchedule(context: Context) {
            androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
}
