/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import mu.KotlinLogging
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.PackageInstaller
import org.calyxos.lupin.getRequest
import org.fdroid.download.HttpDownloaderV2
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.PackageVersionV2
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageInstaller: PackageInstaller,
    private val httpManager: HttpManager,
) {

    private val log = KotlinLogging.logger {}

    @WorkerThread
    internal suspend fun installUpdate(
        packageName: String,
        update: PackageVersionV2,
    ): InstallResult {
        log.info { "Downloading ${update.file.name}" }
        val request = update.file.getRequest(REPO_URL)
        val apkFile = File.createTempFile("apk-", "", context.cacheDir)
        return try {
            HttpDownloaderV2(httpManager, request, apkFile).download()
            log.info { "Installing $packageName" }
            packageInstaller.install(packageName, apkFile) {
                setRequireUserAction(USER_ACTION_NOT_REQUIRED)
            }
            // not throwing exception on negative install result, so we don't re-try to install
        } finally {
            apkFile.delete()
        }
    }

}
