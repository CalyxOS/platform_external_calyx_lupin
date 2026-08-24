/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageManager.GET_SIGNATURES
import android.content.pm.PackageManager.NameNotFoundException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.RepoHelper
import org.calyxos.lupin.TempFileProvider
import org.calyxos.lupin.getRequest
import org.calyxos.lupin.getSharedLibraryVersionCode
import org.fdroid.CompatibilityCheckerImpl
import org.fdroid.UpdateChecker
import org.fdroid.download.HttpDownloaderV2
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.Entry
import org.fdroid.index.v2.IndexV2
import org.fdroid.index.v2.PackageV2
import org.fdroid.index.v2.PackageVersionV2
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

internal const val PACKAGE_NAME_WEBVIEW = "org.calyxos.webview"
internal const val PACKAGE_NAME_CHROME = "org.calyxos.chromium"
internal const val PACKAGE_NAME_TRICHROME_LIB = "org.calyxos.trichromelibrary"

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
                val triChromeResult = checkTriChrome(
                    packageName = packageName,
                    versionCode = update.manifest.versionCode,
                    triChromePackage = index.packages[PACKAGE_NAME_TRICHROME_LIB],
                )
                if (triChromeResult.success) {
                    // install the actual update here (no issue with trichrome)
                    val result = installManager.installUpdate(packageName, update)
                    if (result.pendingUserAction) needConfirmationNotification = true
                    else if (result.isWaitingForUserAction) hasOngoingSessions = true
                } else if (triChromeResult.pendingUserAction) {
                    needConfirmationNotification = true
                    retry = true // retry to install packages that depend on trichrome lib
                } else if (triChromeResult.isWaitingForUserAction) {
                    hasOngoingSessions = true
                    retry = true // retry to install packages that depend on trichrome lib
                } else {
                    log.warn { "Trichrome dependency for $packageName was not satisfied." }
                }
            } catch (e: TriChromeException) {
                log.error(e) { "Error installing update: " }
                // we need a repo update to fix trichrome issues, so don't retry before that
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
    ): PackageVersionV2? {
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
        if (update == null) log.info {
            val p = packageInfo
            "No update found for $packageName, installed is ${p.versionName} (${p.longVersionCode})"
        } else log.info {
            val manifest = update.manifest
            "Update found for $packageName: ${manifest.versionName} (${manifest.versionCode})"
        }
        return update
    }

    /**
     * Checks if the current [packageName] needs first to install [PACKAGE_NAME_TRICHROME_LIB]
     * before installing the update for the given [versionCode].
     *
     * Once ether [PACKAGE_NAME_CHROME] or [PACKAGE_NAME_WEBVIEW] have already been updated to the
     * given [versionCode], it means that [PACKAGE_NAME_TRICHROME_LIB] was already installed
     * since it is a requirement for upgrading both packages.
     *
     * This code assumes that all three packages are always present in the [repoUrl] index
     * and their suggested versions always have the same version code.
     *
     * @return true if trichrome library is either not needed or satisfied,
     * e.g. because it was installed successfully.
     */
    @Throws(TriChromeException::class)
    private suspend fun checkTriChrome(
        packageName: String,
        versionCode: Long,
        triChromePackage: PackageV2?,
    ): InstallResult {
        if (packageName == PACKAGE_NAME_WEBVIEW || packageName == PACKAGE_NAME_CHROME) {
            val triChromeVersionCode =
                packageManager.getSharedLibraryVersionCode(PACKAGE_NAME_TRICHROME_LIB) ?: 0
            if (versionCode > triChromeVersionCode) {
                // tri chrome library has not been updated, yet, so must be updated first
                log.info { "Installing trichrome update first" }
                return installTriChromeLibrary(versionCode, triChromePackage)
            } else {
                log.info { "Trichrome library already has version code $triChromeVersionCode" }
            }
        }
        return InstallResult(STATUS_SUCCESS, null)
    }

    @Throws(TriChromeException::class)
    private suspend fun installTriChromeLibrary(
        versionCode: Long,
        packageV2: PackageV2?,
    ): InstallResult {
        if (packageV2 == null) {
            throw TriChromeException("$PACKAGE_NAME_TRICHROME_LIB was not found in index")
        }
        if (installManager.hasActiveSession(PACKAGE_NAME_TRICHROME_LIB)) {
            log.info { "Active session for $PACKAGE_NAME_TRICHROME_LIB found, skipping." }
            return InstallResult(STATUS_PENDING_USER_ACTION, "trichrome lib needs user confirm")
        }
        val versions = packageV2.versions.values.toList()
        val version = updateChecker.getSuggestedVersion(versions, null)!!
        if (versionCode != version.versionCode) throw TriChromeException(
            "$PACKAGE_NAME_TRICHROME_LIB has versionCode ${version.versionCode}," +
                "but expected $versionCode"
        )
        return installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, version)
    }

}

internal class TriChromeException(msg: String) : Exception(msg)
