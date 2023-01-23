/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.calyxos.lupin.installer.R
import org.calyxos.lupin.installer.state.AppItemState
import org.calyxos.lupin.installer.state.UiState
import javax.inject.Inject

private const val CHANNEL_ID = "SetupWizard"
private const val ONGOING_NOTIFICATION_ID = 1

@AndroidEntryPoint
class AppInstallerService : LifecycleService() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AppInstallerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppInstallerService::class.java)
            context.stopService(intent)
        }
    }

    @Inject
    lateinit var appInstaller: AppInstaller

    private val notificationManager get() = getSystemService(NotificationManager::class.java)
    private val notification: Notification
        get() = Notification.Builder(applicationContext, CHANNEL_ID).build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        lifecycleScope.launchWhenStarted {
            repeatOnLifecycle(STARTED) {
                appInstaller.uiState.collect(::onUiStateChanged)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        val name = "App Installer Channel"
        val channel = NotificationChannel(CHANNEL_ID, name, IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    private fun onUiStateChanged(uiState: UiState) {
        if (uiState is UiState.InstallingApps) {
            val max = (uiState.total / 1024).toInt()
            val progress = (uiState.done / 1024).toInt()
            val notification = Notification.Builder(applicationContext, CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle(getString(R.string.install_notification_title))
                .setContentText(uiState.items.find { it.state == AppItemState.Progress }?.name)
                .setSmallIcon(R.drawable.fdroid_logo)
                .setProgress(max, progress, false)
                .build()
            notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

}
