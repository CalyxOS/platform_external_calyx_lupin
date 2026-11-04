/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.GET_SIGNATURES
import android.content.pm.PackageManager.NameNotFoundException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.calyxos.lupin.RepoHelper
import org.calyxos.lupin.TempFileProvider
import org.calyxos.lupin.getRequest
import org.fdroid.CompatibilityCheckerImpl
import org.fdroid.UpdateChecker
import org.fdroid.download.HttpDownloaderV2
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.Entry
import org.fdroid.index.v2.IndexV2
import org.fdroid.index.v2.PackageVersionV2
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class UpdateManager(
    context: Context,
    private val httpManager: HttpManager,
    private val updateChecker: UpdateChecker,
    private val installManager: InstallManager,
    private val notificationManager: NotificationManager,
    private val repoConfigGetter: RepoConfigGetter = RepoConfigGetter(context),
    private val certificate: String = repoConfigGetter.repoCert,
    private val tempFileProvider: TempFileProvider,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) {

    private val log = KotlinLogging.logger {}

    private val packageManager = context.packageManager
    private val repoUrl = repoConfigGetter.repoUrl

    @Inject
    constructor(
        @ApplicationContext context: Context,
        httpManager: HttpManager,
        installManager: InstallManager,
        notificationManager: NotificationManager,
        tempFileProvider: TempFileProvider,
    ) : this(
        context = context,
        httpManager = httpManager,
        updateChecker = UpdateChecker(CompatibilityCheckerImpl(context.packageManager)),
        notificationManager = notificationManager,
        installManager = installManager,
        tempFileProvider = tempFileProvider,
    )

    suspend fun downloadEntry(): Entry? = withContext(coroutineContext) {
        try {
            log.info { "Downloading entry from $repoUrl" }
            RepoHelper.downloadEntry(tempFileProvider, repoUrl, certificate, httpManager)
        } catch (e: Exception) {
            if (e::class.simpleName == "MockKException") throw e // don't swallow test ex.
            log.error(e) { "Error downloading index:" }
            null
        }
    }

    suspend fun downloadIndex(entry: Entry): IndexV2? = withContext(coroutineContext) {
        val indexFile = tempFileProvider.createTempFile("index-v2-", ".json")
        try {
            log.info { "Downloading index from $repoUrl" }
            val indexRequest = entry.index.getRequest(repoUrl)
            // TODO we could stream the index directly and not save it to disk at all
            //  only need to ensure that we still verify the sha256 hash
            HttpDownloaderV2(httpManager, indexRequest, indexFile).download()
            RepoHelper.getIndex(indexFile)
        } catch (e: Exception) {
            if (e::class.simpleName == "MockKException") throw e // don't swallow test ex.
            log.error(e) { "Error downloading index:" }
            null
        } finally {
            indexFile.delete()
        }
    }

    /**
     * Install all available updates in the given [index].
     *
     * @return true if we should retry updating soon, usually because there was an intermittent
     * error while updating.
     */
    suspend fun updateApps(index: IndexV2): Boolean = withContext(coroutineContext) {
        var hasOngoingSessions = false
        var needConfirmationNotification = false
        var retry = false
        index.packages.forEach { (packageName, packageV2) ->
            log.info { "Checking if $packageName has an update " }
            val packageVersions = packageV2.versions.values.toList()
            try {
                val update = getUpdate(packageName, packageVersions) ?: return@forEach
                if (installManager.hasActiveSession(packageName)) {
                    log.info { "Active session for $packageName found, skipping." }
                    // FIXME Even if user checks manually, this will cause a notification.
                    //  Might be best to wait for Android 14 pre-auth to fix this.
                    needConfirmationNotification = true
                    return@forEach
                }
                val result = installManager.installUpdate(packageName, update.packageVersion)
                if (result.pendingUserAction) needConfirmationNotification = true
                else if (result.isWaitingForUserAction) hasOngoingSessions = true
                else if (result.success) {
                    log.info { "Update for $packageName installed successfully" }
                    notificationManager.showUpdateSuccessNotification(
                        packageName = packageName,
                        name = update.appName,
                        fromVersion = update.installedVersionName,
                        toVersion = update.packageVersion.manifest.versionName,
                    )
                } else {
                    // keep retrying when install failed
                    retry = true
                }
            } catch (e: Exception) {
                if (e::class.simpleName == "MockKException") throw e // don't swallow test ex.
                log.error(e) { "Error installing update: " }
                retry = true
            }
        }
        // we keep active sessions in case user is confirming right now or will be later
        if (needConfirmationNotification) {
            notificationManager.showUserConfirmationRequiredNotification()
        } else if (!hasOngoingSessions) {
            installManager.clearUpOldSession()
        }
        // return true to retry, even if we need confirmation as that may get denied
        retry || needConfirmationNotification
    }

    private fun getUpdate(
        packageName: String,
        packageVersions: List<PackageVersionV2>,
    ): Update? {
        if (packageVersions.isEmpty()) return null
        @Suppress("DEPRECATION")
        @SuppressLint("PackageManagerGetSignatures")
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, GET_SIGNATURES)
        } catch (_: NameNotFoundException) {
            // not installed, so nothing to update
            log.info { "$packageName is not installed, skipping." }
            return null
        }
        val update = updateChecker.getUpdate(packageVersions, packageInfo)
        if (update == null) {
            log.info {
                val p = packageInfo
                "No update found for $packageName, installed is ${p.versionName} (${p.longVersionCode})"
            }
            return null
        } else {
            log.info {
                val manifest = update.manifest
                "Update found for $packageName: ${manifest.versionName} (${manifest.versionCode})"
            }
            val label = packageInfo.applicationInfo?.loadLabel(packageManager)
            return Update(
                packageVersion = update,
                appName = label?.toString() ?: "",
                installedVersionName = packageInfo.versionName ?: ""
            )
        }
    }
}

internal data class Update(
    val packageVersion: PackageVersionV2,
    val appName: String,
    val installedVersionName: String,
)
