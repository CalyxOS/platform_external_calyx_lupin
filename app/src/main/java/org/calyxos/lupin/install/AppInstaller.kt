/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.install

import android.content.Context
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager.GET_SIGNATURES
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.content.pm.PackageManager.INSTALL_SCENARIO_BULK
import android.content.pm.SigningInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.PackageInstaller
import org.calyxos.lupin.state.AppItem
import org.calyxos.lupin.state.AppItemState
import org.calyxos.lupin.state.UiState
import org.fdroid.index.IndexUtils.getPackageSignature
import org.fdroid.index.v2.SignerV2
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppInstaller"
private const val INSTALLER_PACKAGE_NAME = "org.fdroid.fdroid.privileged"

@Singleton
class AppInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageInstaller: PackageInstaller,
) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    /**
     * This provides updated [UiState] only after calling [installApps].
     */
    val uiState = _uiState.asStateFlow()

    internal suspend fun installApps(items: MutableList<AppItem>) {
        _uiState.value = UiState.SelectionComplete(items)
        var done = 0L
        val total = items.sumOf {
            if (it.state is AppItemState.Selectable && it.state.selected) it.apkSize else 0L
        }
        _uiState.value = UiState.InstallingApps(items, done, total)
        items.forEachIndexed { i, item ->
            if (item.state is AppItemState.Selectable && item.state.selected) {
                val progressItem = item.copy(state = AppItemState.Progress)
                val s = UiState.InstallingApps(items.withReplaced(i, progressItem), done, total)
                _uiState.value = s
                // download and install APK, will emit updated state
                val result = installApk(item, s)
                val doneItem = if (result.success) {
                    item.copy(state = AppItemState.Success)
                } else {
                    item.copy(state = AppItemState.Error)
                }
                done += item.apkSize
                currentCoroutineContext().ensureActive()
                _uiState.value =
                    UiState.InstallingApps(items.withReplaced(i, doneItem), done, total)
            } else {
                currentCoroutineContext().ensureActive()
                val showOnlyItem = item.copy(state = AppItemState.ShowOnly)
                _uiState.value =
                    UiState.InstallingApps(items.withReplaced(i, showOnlyItem), done, total)
            }
        }
        _uiState.value = UiState.Done(items)
    }

    private suspend fun installApk(
        item: AppItem,
        state: UiState.InstallingApps,
    ): InstallResult {
        val file = try {
            item.apkGetter { bytesRead ->
                // emit updated state with download progress
                val newState =
                    UiState.InstallingApps(state.items, state.done + bytesRead, state.total)
                _uiState.value = newState
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting APK: ", e)
            return InstallResult(e)
        }
        return try {
            if (!isValidSignature(file, item)) {
                val e = SecurityException("Invalid signature for ${item.packageName}")
                return InstallResult(e)
            }
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

    private fun isValidSignature(file: File, item: AppItem): Boolean {
        val flags = GET_SIGNING_CERTIFICATES or GET_SIGNATURES
        val packageInfo =
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        if (item.packageName != packageInfo.packageName) {
            Log.e(TAG,
                "Package ${item.packageName} expected, but ${packageInfo.packageName} found.")
            return false
        }
        return packageInfo.signingInfo.getSigner() == item.signers
    }

    /**
     * Returns a list of hex-encoded SHA-256 signature hashes in [SignerV2].
     */
    private fun SigningInfo.getSigner(): SignerV2 = SignerV2(
        hasMultipleSigners = hasMultipleSigners(),
        sha256 = if (hasMultipleSigners()) apkContentsSigners.map { signature ->
            getPackageSignature(signature.toByteArray())
        } else signingCertificateHistory.map { signature ->
            getPackageSignature(signature.toByteArray())
        },
    )

    private fun MutableList<AppItem>.withReplaced(index: Int, item: AppItem): MutableList<AppItem> {
        return apply {
            set(index, item)
        }
    }

    private fun SessionParams.setInstallerPackageName(packageName: String) {
        val method = javaClass.methods.find { it.name == "setInstallerPackageName" }
        method?.invoke(this, packageName)
    }

}
