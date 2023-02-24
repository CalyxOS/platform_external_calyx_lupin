/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.calyxos.lupin.updater.ui.ConfirmationActivity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_UPDATE_ID = "updateNotification"
private const val CHANNEL_CONFIRMATION_ID = "confirmationNotification"
internal const val NOTIFICATION_UPDATE_ID = 1
internal const val NOTIFICATION_CONFIRMATION_ID = 2

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val updateChannel = NotificationChannel(
            CHANNEL_UPDATE_ID,
            context.getString(R.string.channel_update_name),
            IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_update_description)
        }
        nm.createNotificationChannel(updateChannel)

        val confirmationChannel = NotificationChannel(
            CHANNEL_CONFIRMATION_ID,
            context.getString(R.string.channel_confirmation_name),
            IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_confirmation_description)
        }
        nm.createNotificationChannel(confirmationChannel)
    }

    fun getUpdateNotification(workerId: UUID): Notification {
        val title = context.getString(R.string.notification_update_title)
        val cancel = context.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(context)
            .createCancelPendingIntent(workerId)
        return NotificationCompat.Builder(context, CHANNEL_UPDATE_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.update)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
    }

    fun showUserConfirmationRequiredNotification() {
        val title = context.getString(R.string.notification_confirmation_title)
        val intent = Intent(context, ConfirmationActivity::class.java).apply {
            flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONFIRMATION_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(R.drawable.security_update_warning)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        if (checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
            nm.notify(NOTIFICATION_CONFIRMATION_ID, notification)
        }
    }

    fun cancelUserConfirmationRequiredNotification() {
        nm.cancel(NOTIFICATION_CONFIRMATION_ID)
    }

}
