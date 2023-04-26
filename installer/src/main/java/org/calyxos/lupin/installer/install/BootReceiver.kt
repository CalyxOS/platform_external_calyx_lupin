/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.install

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation.State.SUCCESS
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.calyxos.lupin.ApkInstaller
import org.calyxos.lupin.installer.install.AppInstaller.Companion.setInstallerPackageNameCompat
import org.calyxos.lupin.installer.state.REPO_PATH
import java.io.File

class BootReceiver : BroadcastReceiver() {
    companion object {
        val DEMOTED_SYSTEM_APPS = mapOf(Pair("com.android.vending", "FakeStore.apk"))
    }

    class SystemAppDemotionWorker(context: Context, workerParameters: WorkerParameters) :
        CoroutineWorker(context, workerParameters) {
        override suspend fun doWork(): Result {
            val apkInstaller = ApkInstaller(applicationContext)
            DEMOTED_SYSTEM_APPS.forEach {
                val packageInfo = try {
                    applicationContext.packageManager.getPackageInfo(
                        it.key,
                        PackageManager.PackageInfoFlags.of(0.toLong())
                    )
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                if (packageInfo == null) {
                    val uninstalledPackageInfo = try {
                        applicationContext.packageManager.getPackageInfo(
                            it.key,
                            PackageManager.PackageInfoFlags.of(
                                PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()
                            )
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                    if (uninstalledPackageInfo != null) {
                        if (!apkInstaller.install(
                                it.key,
                                File(REPO_PATH, it.value)
                            ) { setInstallerPackageNameCompat(INSTALLER_PACKAGE_NAME) }.success
                        ) {
                            return Result.failure()
                        }
                    }
                }
            }
            return Result.success()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val operation = WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<SystemAppDemotionWorker>().setExpedited(
                        OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
                    ).build()
                )
                if (operation.result.get() is SUCCESS) {
                    context.packageManager.setComponentEnabledSetting(
                        ComponentName(context, BootReceiver::class.java),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}
