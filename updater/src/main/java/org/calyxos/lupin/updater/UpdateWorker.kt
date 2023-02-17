/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mu.KotlinLogging

const val WORK_NAME_MANUAL = "manual"
const val WORK_NAME_PERIODIC = "periodic"
private const val CHANNEL_ID = "updateNotification"
private const val NOTIFICATION_ID = 1

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsManager: SettingsManager,
    private val repoManager: RepoManager,
) : CoroutineWorker(context, workerParams) {

    private val log = KotlinLogging.logger {}
    private val notificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            log.error(e) { "Error while running setForeground" }
        }

        // TODO pass isStopped() in lambda to cancel work when system stops us
        val index = repoManager.downloadIndex() ?: return Result.retry()

        // update last checked
        settingsManager.lastCheckedMillis = System.currentTimeMillis()

        // update apps from repo, if index is new
        if (index.repo.timestamp > settingsManager.lastRepoTimestamp) {
            // TODO pass isStopped() in lambda to cancel work when system stops us
            val allUpdated = repoManager.updateApps(index)
            if (allUpdated) settingsManager.lastRepoTimestamp = index.repo.timestamp
        } else {
            log.info { "Repo was not updated" }
        }
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(R.string.notification_title)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        createChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val name = applicationContext.getString(R.string.channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, IMPORTANCE_LOW).apply {
            description = applicationContext.getString(R.string.channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

}
