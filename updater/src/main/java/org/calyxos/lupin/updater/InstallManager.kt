/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageInstaller.EXTRA_SESSION_ID
import android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat.startActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import mu.KotlinLogging
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.PackageInstaller
import org.calyxos.lupin.UserActionRequiredListener
import org.calyxos.lupin.getRequest
import org.fdroid.download.HttpDownloaderV2
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.PackageVersionV2
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// @hide in PackageInstaller.ACTION_CONFIRM_INSTALL
private const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"

@Singleton
class InstallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageInstaller: PackageInstaller,
    private val httpManager: HttpManager,
    private val notificationManager: NotificationManager,
) {

    private val log = KotlinLogging.logger {}

    // @UiThread
    private val manualUpdates = HashMap<String, ManualUpdate>()

    @WorkerThread
    internal suspend fun installUpdate(
        packageName: String,
        update: PackageVersionV2,
    ): InstallResult {
        log.info { "Downloading ${update.file.name}" }
        val request = update.file.getRequest(REPO_URL)
        val apkFile = File.createTempFile("apk-", "", context.cacheDir)
        val userActionListener = UserActionRequiredListener { _, sessionId, intent ->
            onUserConfirmationRequired(packageName, update, sessionId, intent)
        }
        return try {
            HttpDownloaderV2(httpManager, request, apkFile).download()
            log.info { "Installing $packageName" }
            packageInstaller.install(packageName, apkFile, userActionListener) {
                setRequireUserAction(USER_ACTION_NOT_REQUIRED)
            }
            // not throwing exception on negative install result, so we don't re-try to install
        } finally {
            apkFile.delete()
        }
    }

    @UiThread
    private fun onUserConfirmationRequired(
        packageName: String,
        update: PackageVersionV2,
        sessionId: Int,
        intent: Intent,
    ): Boolean {
        log.info { "User confirmation required for $packageName" }
        return if (packageInstaller.canStartActivity()) {
            startConfirmIntent(intent)
            true
        } else {
            val manualUpdate = ManualUpdate(packageName, update, sessionId, intent)
            manualUpdates[packageName] = manualUpdate
            false
        }
    }

    @UiThread
    fun onUserAttentionReceived(activity: Activity) {
        // if our process got killed while we waited for user attention, this can be empty
        if (manualUpdates.isEmpty()) setManualUpdatesFromSessions()

        manualUpdates.forEach { (packageName, manualUpdate) ->
            log.info { "Opening confirm dialog for $packageName (${manualUpdate.sessionId})" }
            // need to use an activity to launch, so we don't need FLAG_ACTIVITY_NEW_TASK
            // which seems to break launching several activities at once
            activity.startActivity(manualUpdate.intent)
        }
        manualUpdates.clear()
        notificationManager.cancelUserConfirmationRequiredNotification()
        clearUpOldSession()
    }

    @UiThread
    private fun setManualUpdatesFromSessions() {
        log.info { "Finding sessions to confirm..." }
        val packageInstaller = context.packageManager.packageInstaller
        packageInstaller.mySessions.forEach { sessionInfo ->
            log.info { "session for ${sessionInfo.appPackageName}" }
            log.info { "  sessionId: ${sessionInfo.sessionId}" }
            log.info { "  isCommitted: ${sessionInfo.isCommitted}" }
            log.info { "  isSealed: ${sessionInfo.isSealed}" }
            log.info { "  requireUserAction: ${sessionInfo.requireUserAction}" }
            if (sessionInfo.isCommitted && sessionInfo.isSealed &&
                sessionInfo.appPackageName != null) {
                val packageName = sessionInfo.appPackageName ?: error("sessionInfo no packageName")
                manualUpdates[packageName] = ManualUpdate(
                    packageName = packageName,
                    packageVersion = null,
                    sessionId = sessionInfo.sessionId,
                    intent = recreateConfirmIntent(sessionInfo.sessionId),
                )
            }
        }
    }

    private fun clearUpOldSession() {
        val packageInstaller = context.packageManager.packageInstaller
        packageInstaller.mySessions.forEach { sessionInfo ->
            if (sessionInfo.isCommitted) packageInstaller.abandonSession(sessionInfo.sessionId)
        }
    }

    private fun startConfirmIntent(intent: Intent) {
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        startActivity(context, intent, null)
    }

    private fun recreateConfirmIntent(sessionId: Int) = Intent(ACTION_CONFIRM_INSTALL).apply {
        // TODO need to setPackage(getPackageInstallerPackageName()) as well?
        putExtra(EXTRA_SESSION_ID, sessionId)
    }

}

internal data class ManualUpdate(
    val packageName: String,
    val packageVersion: PackageVersionV2?,
    val sessionId: Int,
    val intent: Intent,
)
