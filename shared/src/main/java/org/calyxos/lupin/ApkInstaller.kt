/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: 2019-2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 *
 *  Based on code from com.android.packageinstaller.InstallInstalling
 *  frameworks/base/packages/PackageInstaller/src/com/android/packageinstaller/InstallInstalling.java
 */

package org.calyxos.lupin

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME
import android.content.pm.PackageInstaller.EXTRA_SESSION_ID
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat.startActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private val TAG = PackageInstaller::class.java.simpleName
private const val BROADCAST_ACTION = "com.android.packageinstaller.ACTION_INSTALL_COMMIT"

@VisibleForTesting
const val STATUS_WAITING_FOR_USER_ACTION = Int.MAX_VALUE - 1

data class InstallResult(
    val status: Int,
    val msg: String?,
    val exception: Exception? = null
) {
    constructor(exception: Exception) : this(Int.MAX_VALUE, null, exception)

    val success get() = status == STATUS_SUCCESS

    val pendingUserAction get() = status == STATUS_PENDING_USER_ACTION

    /**
     * We have just shown a confirmation dialog to the user,
     * but due to bugs in AOSP's PackageInstallerActivity can't get a result, reliably.
     * So we can't mess with existing sessions
     * as they might still be in the process of being confirmed.
     */
    val isWaitingForUserAction get() = status == STATUS_WAITING_FOR_USER_ACTION
}

/**
 * A convenience class for installing packages from files.
 * Not intended for installing multiple packages concurrently.
 * Assumes that you get the [InstallResult] before starting a new installation.
 */
@Singleton
class ApkInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val installer: PackageInstaller = pm.packageInstaller
    private val intentSender: IntentSender
        get() {
            val broadcastIntent = Intent(BROADCAST_ACTION).apply {
                setPackage(context.packageName)
                flags = FLAG_RECEIVER_FOREGROUND
            }
            val pendingIntent = // needs to be mutable, otherwise no extras
                getBroadcast(context, 0, broadcastIntent, FLAG_UPDATE_CURRENT or FLAG_MUTABLE)

            return pendingIntent.intentSender
        }

    @Throws(IOException::class, SecurityException::class)
    suspend fun install(
        packageName: String,
        packageFile: File,
        userActionListener: UserActionRequiredListener = userActionRequiredListener,
        sessionConfig: (SessionParams.() -> Unit)? = null
    ): InstallResult = suspendCancellableCoroutine { cont ->
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                if (i.action != BROADCAST_ACTION) return
                val result = onBroadcastReceived(i, packageName, userActionListener)
                if (result != null) {
                    context.unregisterReceiver(this)
                    cont.resume(result)
                }
            }
        }
        context.registerReceiver(broadcastReceiver, IntentFilter(BROADCAST_ACTION))
        cont.invokeOnCancellation { context.unregisterReceiver(broadcastReceiver) }

        try {
            performInstall(packageName, packageFile, sessionConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing $packageName", e)
            context.unregisterReceiver(broadcastReceiver)
            cont.resume(InstallResult(e))
        }
    }

    @Throws(IOException::class, SecurityException::class)
    private fun performInstall(
        packageName: String,
        packageFile: File,
        sessionConfig: (SessionParams.() -> Unit)?
    ) {
        if (!packageFile.isFile) throw IOException("Cannot read package file $packageFile")

        val params = SessionParams(MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(packageFile.length())
            if (sessionConfig != null) sessionConfig()
            // Don't set more sessionParams intentionally here.
            // We saw strange permission issues when doing setInstallReason()
            // or setting installFlags.
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            val sizeBytes = packageFile.length()
            packageFile.inputStream().use { inputStream ->
                session.openWrite(packageFile.name, 0, sizeBytes).use { out ->
                    inputStream.copyTo(out)
                    session.fsync(out)
                }
            }
            session.commit(intentSender)
        }
    }

    /**
     * Call this when receiving the [STATUS_PENDING_USER_ACTION] intent
     * for the [PackageInstaller.Session].
     *
     * @return null when user action was required or a proper [InstallResult].
     * In case of null, you should continue to listen to broadcast and only unregister
     * when we have an [InstallResult].
     */
    @UiThread
    private fun onBroadcastReceived(
        i: Intent,
        expectedPackageName: String,
        userActionListener: UserActionRequiredListener
    ): InstallResult? {
        val packageName = i.getStringExtra(EXTRA_PACKAGE_NAME)
        // packageName is not always set, e.g. for STATUS_PENDING_USER_ACTION
        if (packageName != null && packageName != expectedPackageName) {
            Log.w(TAG, "Expected $expectedPackageName, but got $packageName.")
            return null
        }
        val result = InstallResult(
            status = i.getIntExtra(EXTRA_STATUS, Int.MIN_VALUE),
            msg = i.getStringExtra(EXTRA_STATUS_MESSAGE)
        )
        Log.d(
            TAG,
            "Received result for $expectedPackageName: status=${result.status} ${result.msg}"
        )
        if (result.pendingUserAction) {
            @Suppress("DEPRECATION") // there's no getIntent() method we can use instead
            val intent = i.extras?.get(EXTRA_INTENT) as Intent
            val userWasAsked = userActionListener.onUserConfirmationRequired(
                packageName = expectedPackageName,
                sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1),
                intent = intent
            )
            return if (userWasAsked) InstallResult(STATUS_WAITING_FOR_USER_ACTION, null) else result
        }
        return result
    }

    fun canStartActivity(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == IMPORTANCE_FOREGROUND
    }

    private val userActionRequiredListener = UserActionRequiredListener { _, _, intent ->
        if (canStartActivity()) {
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(context, intent, null)
            true
        } else {
            false
        }
    }
}

fun PackageManager.getSharedLibraryVersionCode(packageName: String): Long? {
    return getSharedLibraries(PackageInfoFlags.of(0)).mapNotNull {
        if (it.declaringPackage.packageName == packageName) {
            it.longVersion
        } else {
            null
        }
    }.maxOrNull() // getting highest installed version code
}
