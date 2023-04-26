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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.calyxos.lupin.installer.PackageInstaller
import org.calyxos.lupin.installer.install.AppInstaller.Companion.setInstallerPackageName
import org.calyxos.lupin.installer.state.REPO_PATH
import java.io.File


class BootReceiver : BroadcastReceiver() {
    companion object {
        val DEMOTED_SYSTEM_APPS = mapOf(Pair("org.fdroid.fdroid", "F-Droid.apk"))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    DEMOTED_SYSTEM_APPS.forEach {
                        PackageInstaller(context.applicationContext).install(
                            it.key,
                            File(REPO_PATH, it.value)
                        ) {
                            setInstallScenario(PackageManager.INSTALL_SCENARIO_BULK)
                            setInstallerPackageName(INSTALLER_PACKAGE_NAME)
                        }
                    }
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