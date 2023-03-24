/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mu.KotlinLogging

const val WORK_NAME_MANUAL = "manual"
const val WORK_NAME_PERIODIC = "periodic"

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsManager: SettingsManager,
    private val updateManager: UpdateManager,
    private val notificationManager: NotificationManager,
) : CoroutineWorker(context, workerParams) {

    private val log = KotlinLogging.logger {}

    override suspend fun doWork(): Result {
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            log.error(e) { "Error while running setForeground" }
        }

        // TODO pass isStopped() in lambda to cancel work when system stops us
        val index = updateManager.downloadIndex() ?: return Result.retry()

        // update last checked
        settingsManager.lastCheckedMillis = System.currentTimeMillis()

        // update apps from repo, if index is new
        if (index.repo.timestamp > settingsManager.lastRepoTimestamp) {
            // TODO pass isStopped() in lambda to cancel work when system stops us
            val updateResult = updateManager.updateApps(index)
            if (!updateResult) settingsManager.lastRepoTimestamp = index.repo.timestamp
        } else {
            log.info { "Repo was not updated" }
        }
        return Result.success()
    }

    private fun createForegroundInfo() = ForegroundInfo(
        NOTIFICATION_UPDATE_ID,
        notificationManager.getUpdateNotification(id),
    )

}
