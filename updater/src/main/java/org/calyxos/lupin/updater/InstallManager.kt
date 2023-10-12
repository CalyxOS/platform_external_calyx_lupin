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
import android.content.pm.PackageInstaller.SessionInfo
import android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat.startActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.calyxos.lupin.ApkInstaller
import org.calyxos.lupin.InstallResult
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
    private val apkInstaller: ApkInstaller,
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
        // FIXME it might make sense to download APKs to predictable location and resume downloads
        val apkFile = File.createTempFile("apk-", "", context.cacheDir)
        val userActionListener = UserActionRequiredListener { _, sessionId, intent ->
            onUserConfirmationRequired(packageName, update, sessionId, intent)
        }
        return try {
            HttpDownloaderV2(httpManager, request, apkFile).download()
            log.info { "Installing $packageName" }
            apkInstaller.install(packageName, apkFile, true, userActionListener) {
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
        return if (apkInstaller.canStartActivity()) {
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(context, intent, null)
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
        // We can't reliably get informed when all updates have been completed
        // (e.g. user taps outside the confirmation dialog results in no callbacks),
        // so we simply finish our activity and clean up old sessions in another place.
        // The confirmation dialogs still stay open for the user to respond to.
        activity.finish()
    }

    @UiThread
    private fun setManualUpdatesFromSessions() {
        log.info { "Finding sessions to confirm..." }
        val packageInstaller = context.packageManager.packageInstaller
        packageInstaller.mySessions.forEach { sessionInfo ->
            sessionInfo.log()
            if (sessionInfo.isOkToUse()) {
                val packageName = sessionInfo.appPackageName ?: error("sessionInfo no packageName")
                manualUpdates[packageName] = sessionInfo.toManualUpdate(packageName)
            }
        }
    }

    /**
     * Returns true if the given [packageName] has a usable installer session
     * and handles this session once [onUserAttentionReceived] gets called.
     */
    suspend fun hasActiveSession(packageName: String): Boolean {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionInfo = packageInstaller.mySessions.find { sessionInfo ->
            sessionInfo.appPackageName == packageName && sessionInfo.isOkToUse()
        } ?: return false
        withContext(Dispatchers.Main) {
            manualUpdates[packageName] = sessionInfo.toManualUpdate(packageName)
        }
        return true
    }

    fun clearUpOldSession() {
        log.info { "Cleaning up old sessions..." }
        val packageInstaller = context.packageManager.packageInstaller
        packageInstaller.mySessions.forEach { sessionInfo ->
            sessionInfo.log()
            if (sessionInfo.isCommitted) packageInstaller.abandonSession(sessionInfo.sessionId)
        }
    }

    private fun recreateConfirmIntent(sessionId: Int) = Intent(ACTION_CONFIRM_INSTALL).apply {
        // TODO need to setPackage(getPackageInstallerPackageName()) as well
        //  or can there be only one installer?
        putExtra(EXTRA_SESSION_ID, sessionId)
    }

    private fun SessionInfo.isOkToUse(): Boolean {
        return isCommitted && isSealed && appPackageName != null
    }

    private fun SessionInfo.toManualUpdate(
        packageName: String = appPackageName ?: error("sessionInfo no packageName"),
    ) = ManualUpdate(
        packageName = packageName.also {
            check(appPackageName == packageName) { "package names do not match" }
        },
        packageVersion = null,
        sessionId = sessionId,
        intent = recreateConfirmIntent(sessionId),
    )

    private fun SessionInfo.log() {
        log.info { "session for $appPackageName" }
        log.info { "  sessionId: $sessionId" }
        log.info { "  isCommitted: $isCommitted" }
        log.info { "  isSealed: $isSealed" }
        log.info { "  requireUserAction: $requireUserAction" }
    }

}

internal data class ManualUpdate(
    val packageName: String,
    val packageVersion: PackageVersionV2?,
    val sessionId: Int,
    val intent: Intent,
)
