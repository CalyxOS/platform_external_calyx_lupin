/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2019-2022 The Calyx Institute
 *
 *  Based on code from com.android.packageinstaller.InstallInstalling
 *  frameworks/base/packages/PackageInstaller/src/com/android/packageinstaller/InstallInstalling.java
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.calyxos.lupin

import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

data class InstallResult(
    val status: Int,
    val msg: String?,
    val exception: Exception? = null,
) {
    constructor(exception: Exception) : this(-1, null, exception)

    val success = status == STATUS_SUCCESS
}

/**
 * A convenience class for installing packages from files.
 * Not intended for installing multiple packages concurrently.
 * Assumes that you get the [InstallResult] before starting a new installation.
 */
@Singleton
class PackageInstaller @Inject constructor(@ApplicationContext private val context: Context) {

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
        sessionConfig: (SessionParams.() -> Unit)? = null,
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
        sessionConfig: (SessionParams.() -> Unit)?,
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
    private fun onBroadcastReceived(
        i: Intent,
        expectedPackageName: String,
        userActionListener: UserActionRequiredListener,
    ): InstallResult? {
        val packageName = i.getStringExtra(EXTRA_PACKAGE_NAME)
        check(packageName == null || packageName == expectedPackageName) {
            "Expected $expectedPackageName, but got $packageName."
        }
        val result = InstallResult(
            status = i.getIntExtra(EXTRA_STATUS, Int.MIN_VALUE),
            msg = i.getStringExtra(EXTRA_STATUS_MESSAGE),
        )
        Log.d(
            TAG,
            "Received result for $expectedPackageName: status=${result.status} ${result.msg}"
        )
        if (result.status == STATUS_PENDING_USER_ACTION) {
            val intent = i.extras?.get(Intent.EXTRA_INTENT) as Intent
            val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
            userActionListener.onUserConfirmationRequired(expectedPackageName, sessionId, intent)
            return null
        }
        return result
    }

    private val userActionRequiredListener = UserActionRequiredListener { _, _, intent ->
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        startActivity(context, intent, null)
    }
}

fun interface UserActionRequiredListener {
    /**
     * Called when user confirmation is required for the installation of the [packageName].
     * Usually, you want to start an activity with the given [intent].
     * However, you need to ensure that your app is allowed to launch an activity at this time.
     *
     * @param packageName the package name of the package that requires user confirmation.
     * @param sessionId The ID of the [PackageInstaller.SessionInfo] or -1, if not known.
     * @param intent The [Intent] we can start to prompt user confirmation in the UI.
     */
    fun onUserConfirmationRequired(packageName: String, sessionId: Int, intent: Intent)
}

fun PackageManager.getSharedLibraryVersionCode(packageName: String): Long? {
    return getSharedLibraries(PackageInfoFlags.of(0)).mapNotNull {
        if (it.declaringPackage.packageName == packageName) it.longVersion
        else null
    }.maxOrNull() // getting highest installed version code
}
