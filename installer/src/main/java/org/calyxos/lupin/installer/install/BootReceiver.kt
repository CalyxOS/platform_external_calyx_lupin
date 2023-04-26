/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.AndroidEntryPoint
import org.calyxos.lupin.installer.PackageInstaller
import org.calyxos.lupin.installer.install.AppInstaller.Companion.setInstallerPackageName
import org.calyxos.lupin.installer.state.REPO_PATH
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var packageInstaller: PackageInstaller

    companion object {
        val DEMOTED_SYSTEM_APPS = mapOf("org.fdroid.fdroid" to "F-Droid.apk")
    }

    inner class SystemAppDemotionWorker(context: Context, workerParameters: WorkerParameters) :
        CoroutineWorker(context, workerParameters) {
        override suspend fun doWork(): Result {
            DEMOTED_SYSTEM_APPS.forEach {
                try {
                    applicationContext.packageManager.getPackageInfo(
                        it.key,
                        PackageManager.PackageInfoFlags.of(0.toLong())
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    if (!packageInstaller.install(
                            it.key,
                            File(REPO_PATH, it.value)
                        ) { setInstallerPackageName(INSTALLER_PACKAGE_NAME) }.success
                    ) {
                        return Result.failure()
                    }
                }
            }
            return Result.success()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<SystemAppDemotionWorker>().setExpedited(
                        OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
                    ).build()
                )
            }
        }
    }
}
