/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager.INSTALL_SCENARIO_BULK
import android.util.Log
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.channelFlow

private const val TAG = "AppInstaller"
private const val INSTALLER_PACKAGE_NAME = "org.fdroid.fdroid.privileged"

class AppInstaller(
    private val packageInstaller: PackageInstaller,
) {

    // we are using a channelFlow so we update download progress async
    internal suspend fun installApps(items: MutableList<AppItem>) = channelFlow {
        var done = 0L
        val total = items.sumOf {
            if (it.state is AppItemState.Selectable && it.state.selected) it.apkSize else 0L
        }
        items.forEachIndexed { i, item ->
            if (item.state is AppItemState.Selectable && item.state.selected) {
                val progressItem = item.copy(state = AppItemState.Progress)
                val state =
                    UiState.InstallingApps(items.apply { set(i, progressItem) }, done, total)
                send(state)
                // download and install APK, will emit updated state
                val result = installApk(item, state)
                val doneItem = if (result.success) {
                    item.copy(state = AppItemState.Success)
                } else {
                    item.copy(state = AppItemState.Error)
                }
                done += item.apkSize
                currentCoroutineContext().ensureActive()
                send(UiState.InstallingApps(items.apply { set(i, doneItem) }, done, total))
            } else {
                currentCoroutineContext().ensureActive()
                val showOnlyItem = item.copy(state = AppItemState.ShowOnly)
                send(UiState.InstallingApps(items.apply { set(i, showOnlyItem) }, done, total))
            }
        }
        send(UiState.Done(items))
    }

    private suspend fun ProducerScope<UiState>.installApk(
        item: AppItem,
        state: UiState.InstallingApps,
    ): InstallResult {
        val file = try {
            item.apkGetter { bytesRead ->
                // emit updated state with download progress
                val newState =
                    UiState.InstallingApps(state.items, state.done + bytesRead, state.total)
                send(newState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting APK: ", e)
            return InstallResult(e)
        }
        return try {
            packageInstaller.install(item.packageName, file) {
                setInstallScenario(INSTALL_SCENARIO_BULK)
                setInstallerPackageName(INSTALLER_PACKAGE_NAME)
            }
        } finally {
            try {
                file.delete()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun SessionParams.setInstallerPackageName(packageName: String) {
        val method = javaClass.methods.find { it.name == "setInstallerPackageName" }
        method?.invoke(this, packageName)
    }

}
