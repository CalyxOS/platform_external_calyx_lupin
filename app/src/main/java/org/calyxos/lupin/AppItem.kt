/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import androidx.core.os.LocaleListCompat
import org.fdroid.LocaleChooser.getBestLocale
import org.fdroid.index.v2.PackageV2
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
    val icon: Any?,
    val name: String,
    val summary: String,
    val apkGetter: suspend (DownloadListener) -> File,
    val apkSize: Long,
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
        icon = result.iconGetter(packageV2.metadata.icon.getBestLocale(locales)?.name),
        name = packageV2.metadata.name.getBestLocale(locales) ?: "Unknown",
        summary = packageV2.metadata.summary.getBestLocale(locales) ?: "",
        apkGetter = { downloadListener ->
            result.apkGetter(packageV2.versions.values.first().file.name, downloadListener)
        },
        apkSize = packageV2.versions.values.first().file.size ?: 0,
        state = AppItemState.Selectable(true),
    )

    /**
     * Copies the given [item], retaining its [packageName], [icon] and [state].
     */
    constructor(
        item: AppItem,
        result: RepoResult,
        packageV2: PackageV2,
        locales: LocaleListCompat,
    ) : this(
        packageName = item.packageName,
        icon = item.icon,
        name = packageV2.metadata.name.getBestLocale(locales) ?: "Unknown",
        summary = packageV2.metadata.summary.getBestLocale(locales) ?: "",
        apkGetter = { downloadListener ->
            result.apkGetter(packageV2.versions.values.first().file.name, downloadListener)
        },
        apkSize = packageV2.versions.values.first().file.size ?: 0,
        state = item.state,
    )
}
