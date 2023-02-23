/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.state

import android.util.Log
import androidx.core.os.LocaleListCompat
import org.fdroid.LocaleChooser.getBestLocale
import org.fdroid.index.v2.PackageV2
import org.fdroid.index.v2.SignerV2
import java.io.File

sealed class AppItemState {
    object ShowOnly : AppItemState()
    class Selectable(val selected: Boolean) : AppItemState() {
        fun invert() = Selectable(!selected)
    }

    object Progress : AppItemState()
    object Success : AppItemState()
    object Error : AppItemState()
}

fun interface DownloadListener {
    suspend fun bytesRead(numBytes: Long)
}

data class AppItem(
    val packageName: String,
    val versionCode: Long,
    val icon: Any?,
    val name: String,
    val summary: String,
    val apkGetter: suspend (DownloadListener) -> File,
    val apkSize: Long,
    val signers: SignerV2,
    val isOnlineOnly: Boolean,
    val state: AppItemState,
) {
    /**
     * Creates a new selectable item that is pre-selected.
     */
    constructor(
        packageName: String,
        result: RepoResult,
        packageV2: PackageV2,
        locales: LocaleListCompat,
    ) : this(
        packageName = packageName,
        versionCode = packageV2.getVersionCode(),
        icon = result.iconGetter(packageV2.metadata.icon.getBestLocale(locales)?.name),
        name = packageV2.getName(locales),
        summary = packageV2.getSummary(locales),
        apkGetter = { downloadListener ->
            result.apkGetter(packageV2.versions.values.first().file.name, downloadListener)
        },
        apkSize = packageV2.getApkSize(),
        signers = packageV2.getSigner() ?: error("No signer"),
        isOnlineOnly = packageV2.isOnlineOnly(),
        state = AppItemState.Selectable(true),
    )

    /**
     * Copies the given [item], retaining its [packageName], [icon], [signers] and [state].
     */
    constructor(
        item: AppItem,
        result: RepoResult,
        packageV2: PackageV2,
        locales: LocaleListCompat,
    ) : this(
        packageName = item.packageName,
        versionCode = packageV2.getVersionCode(),
        icon = item.icon,
        name = packageV2.getName(locales),
        summary = packageV2.getSummary(locales),
        apkGetter = { downloadListener ->
            result.apkGetter(packageV2.versions.values.first().file.name, downloadListener)
        },
        apkSize = packageV2.getApkSize(),
        signers = item.signers,
        isOnlineOnly = packageV2.isOnlineOnly(),
        state = item.state,
    )

    /**
     * Returns true if the given [packageV2] is a valid update for the current item.
     */
    fun isValidUpdate(packageV2: PackageV2): Boolean {
        if (signers != packageV2.getSigner()) {
            Log.w("AppItem", "Received unexpected signer for: $packageName")
        }
        return isOnlineOnly || packageV2.getVersionCode() > versionCode
    }
}

private fun PackageV2.getVersionCode(): Long {
    return versions.values.first().versionCode
}

private fun PackageV2.getName(locales: LocaleListCompat): String {
    return metadata.name.getBestLocale(locales) ?: "Unknown"
}

private fun PackageV2.getSummary(locales: LocaleListCompat): String {
    return metadata.summary.getBestLocale(locales) ?: ""
}

private fun PackageV2.getApkSize(): Long {
    return versions.values.first().file.size ?: 0
}

fun PackageV2.getSigner(): SignerV2? {
    return versions.values.first().signer
}

private fun PackageV2.isOnlineOnly(): Boolean {
    return metadata.categories.contains(CATEGORY_ONLINE_ONLY)
}
