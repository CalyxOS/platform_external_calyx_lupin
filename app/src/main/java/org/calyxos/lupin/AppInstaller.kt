/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager.INSTALL_SCENARIO_BULK
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.coroutineContext

private const val INSTALLER_PACKAGE_NAME = "org.fdroid.fdroid.privileged"

class AppInstaller(
    private val packageInstaller: PackageInstaller,
) {

    internal suspend fun installApps(items: MutableList<AppItem>) = flow {
        var done = 0
        val total = items.count { it.state is AppItemState.Selectable && it.state.selected }
        items.forEachIndexed { i, item ->
            if (item.state is AppItemState.Selectable && item.state.selected) {
                val progressItem = item.copy(state = AppItemState.Progress)
                emit(UiState.InstallingApps(items.apply { set(i, progressItem) }, done, total))
                val result = packageInstaller.install(item.packageName, item.apk) {
                    setInstallScenario(INSTALL_SCENARIO_BULK)
                    setInstallerPackageName(INSTALLER_PACKAGE_NAME)
                }
                val doneItem = if (result.success) {
                    item.copy(state = AppItemState.Success)
                } else {
                    item.copy(state = AppItemState.Error)
                }
                done++
                coroutineContext.ensureActive()
                emit(UiState.InstallingApps(items.apply { set(i, doneItem) }, done, total))
            } else {
                coroutineContext.ensureActive()
                val showOnlyItem = item.copy(state = AppItemState.ShowOnly)
                emit(UiState.InstallingApps(items.apply { set(i, showOnlyItem) }, done, total))
            }
        }
        emit(UiState.Done(items))
    }

    private fun SessionParams.setInstallerPackageName(packageName: String) {
        val method = javaClass.methods.find { it.name == "setInstallerPackageName" }
        method?.invoke(this, packageName)
    }

}
