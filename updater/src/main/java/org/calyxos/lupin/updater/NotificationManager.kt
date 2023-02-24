/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_UPDATE_ID = "updateNotification"
internal const val NOTIFICATION_UPDATE_ID = 1

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(android.app.NotificationManager::class.java)

    init {
        val updateName = context.getString(R.string.channel_update_name)
        val updateChannel = NotificationChannel(CHANNEL_UPDATE_ID, updateName, IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_update_description)
        }
        nm.createNotificationChannel(updateChannel)
    }

    fun getUpdateNotification(workerId: UUID): Notification {
        val title = context.getString(R.string.notification_title)
        val cancel = context.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(context)
            .createCancelPendingIntent(workerId)
        return NotificationCompat.Builder(context, CHANNEL_UPDATE_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
    }

}
