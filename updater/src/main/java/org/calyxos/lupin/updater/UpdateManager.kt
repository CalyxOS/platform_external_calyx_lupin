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
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.RepoHelper.downloadIndex
import org.calyxos.lupin.getSharedLibraryVersionCode
import org.fdroid.CompatibilityCheckerImpl
import org.fdroid.UpdateChecker
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.IndexV2
import org.fdroid.index.v2.PackageV2
import org.fdroid.index.v2.PackageVersionV2
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

internal const val REPO_URL = "https://calyxos.gitlab.io/calyx-fdroid-repo/fdroid/repo"
internal const val PACKAGE_NAME_WEBVIEW = "com.android.webview"
internal const val PACKAGE_NAME_CHROME = "org.chromium.chrome"
internal const val PACKAGE_NAME_TRICHROME_LIB = "org.chromium.trichromelibrary"

@Singleton
class UpdateManager(
    private val context: Context,
    private val httpManager: HttpManager,
    private val updateChecker: UpdateChecker,
    private val installManager: InstallManager,
    private val notificationManager: NotificationManager,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) {

    private val log = KotlinLogging.logger {}

    private val packageManager = context.packageManager

    @Inject
    constructor(
        @ApplicationContext context: Context,
        httpManager: HttpManager,
        installManager: InstallManager,
        notificationManager: NotificationManager,
    ) : this(
        context = context,
        httpManager = httpManager,
        updateChecker = UpdateChecker(CompatibilityCheckerImpl(context.packageManager)),
        notificationManager = notificationManager,
        installManager = installManager,
    )

    suspend fun downloadIndex(): IndexV2? = withContext(coroutineContext) {
        try {
            log.info { "Downloading index from $REPO_URL" }
            downloadIndex(context, REPO_URL, CERT, httpManager)
        } catch (e: Exception) {
            if (e::class.simpleName == "MockKException") throw e // don't swallow test ex.
            log.error(e) { "Error downloading index:" }
            null
        }
    }

    /**
     * Install all available updates in the given [index].
     *
     * @return true if we should retry updating soon, usually because there was an intermittent
     * error while updating.
     */
    suspend fun updateApps(index: IndexV2): Boolean = withContext(coroutineContext) {
        var userConfirmation = false
        var retry = false
        index.packages.forEach { (packageName, packageV2) ->
            if (installManager.hasActiveSession(packageName)) {
                log.info { "Active session for $packageName found, skipping." }
                userConfirmation = true
                return@forEach
            }
            log.info { "Checking if $packageName has an update " }
            val packageVersions = packageV2.versions.values.toList()
            try {
                val update = getUpdate(packageName, packageVersions) ?: return@forEach
                // TODO re-try chrome/webview when trichrome needs user confirmation
                val triChromeOk = checkTriChrome(
                    packageName = packageName,
                    versionCode = update.manifest.versionCode,
                    triChromePackage = index.packages[PACKAGE_NAME_TRICHROME_LIB],
                )
                if (triChromeOk) {
                    val result = installManager.installUpdate(packageName, update)
                    if (result.pendingUserAction) userConfirmation = true
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
        if (userConfirmation) notificationManager.showUserConfirmationRequiredNotification()
        else installManager.clearUpOldSession()
        retry
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
        } catch (e: NameNotFoundException) {
            // not installed, so nothing to update
            return null
        }
        return updateChecker.getUpdate(packageVersions, packageInfo)
    }

    /**
     * Checks if the current [packageName] needs first to install [PACKAGE_NAME_TRICHROME_LIB]
     * before installing the update for the given [versionCode].
     *
     * Once ether [PACKAGE_NAME_CHROME] or [PACKAGE_NAME_WEBVIEW] have already been updated to the
     * given [versionCode], it means that [PACKAGE_NAME_TRICHROME_LIB] was already installed
     * since it is a requirement for upgrading both packages.
     *
     * This code assumes that all three packages are always present in the [REPO_URL] index
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
    ): Boolean {
        if (packageName == PACKAGE_NAME_WEBVIEW || packageName == PACKAGE_NAME_CHROME) {
            val triChromeVersionCode =
                packageManager.getSharedLibraryVersionCode(PACKAGE_NAME_TRICHROME_LIB) ?: 0
            if (versionCode > triChromeVersionCode) {
                // tri chrome library has not been updated, yet, so must be updated first
                log.info { "Installing trichrome update first" }
                return installTriChromeLibrary(versionCode, triChromePackage).success
            }
        }
        return true
    }

    @Throws(TriChromeException::class)
    private suspend fun installTriChromeLibrary(
        versionCode: Long,
        packageV2: PackageV2?,
    ): InstallResult {
        if (packageV2 == null) {
            throw TriChromeException("$PACKAGE_NAME_TRICHROME_LIB was not found in index")
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

private const val CERT =
    "30820503308202eba003020102020451af9e01300d06092a864886f70d01010b050030323110300e06" +
        "0355040b1307462d44726f6964311e301c060355040313156c6f63616c686f73742e6c6f63616c646f6d6169" +
        "6e301e170d3139303832393233303435335a170d3437303131343233303435335a30323110300e060355040b" +
        "1307462d44726f6964311e301c060355040313156c6f63616c686f73742e6c6f63616c646f6d61696e308202" +
        "22300d06092a864886f70d01010105000382020f003082020a0282020100da473e72cf0e202dd31ea6e229f8" +
        "957eb8c2491b3c99a654f9430da509117fce3a7a9aa75b0203375fd9aefd62243b8c05e0bc2c07d414b51c7f" +
        "626ea3fb9c17ccbba293f694117827f3f1b02c1bb81cbda4e352c2f0e126348ddda247b053a3c310ba6bad0f" +
        "15bfe27362a4f445ffc296e8e9f3e749729b0c22393cb3449fc33575a0d2798ba171abe6537b7dcce48be2b0" +
        "2d97ccbb918790b63443800e48762cc691dc5b68a532c2c83c14aecde41f56d2a4309651d6216c85d172057c" +
        "edabc5f5290850bb0160a346f860b4e472c3eefed9c5c5f66f8485bdcd5da19acabfe9389ece8ed6a485ad99" +
        "82de69a1d937ef394becf0815b42d8e88c948973500e5e35bb3760cb92f6516521e046faf8bc223ef625c457" +
        "5ca5edff58459879dd658291de027ecc387a11c4c1fa1982f44684c5c05b7dc3969625437be3cecc4a3406a2" +
        "9a0bedd6e51e569348c91e621dcbb415d0d3dba2fb07316c0070f8822362bcea7911e2105cc04a06e493ce3e" +
        "1560a9a00ec0d44b753c5c36dc95ff95976029aae140ab56304f7e5891d755099c3deb8f5b1ee662aaadc3fe" +
        "7fab74c0175a509ce28de7072268bf598329d389f27623a4677645394c9cc2a8391fa9bd09b3570fe0e258be" +
        "e1e8029d7916603e20c6972c6a7bfda2c26f691e84639874cd3aafb368ba51a4ce038ce84a945f6d604e6af3" +
        "ede5da58040972e053ad28c3d2890203010001a321301f301d0603551d0e041604142bf6de20fa9e51b8bce0" +
        "dd4b3497699fdd07af64300d06092a864886f70d01010b050003820201000ca2c66797928e49b17813dceeea" +
        "4fb64dbd675960756d85775b198754bef8bafc872f6f2ee594e4bef6b9c57ce701a95425a6eae7102e77ad4c" +
        "c251e9bfb56087a8ac14b175938560a9e8419587563c55c29e584fde5899b9c80aa3b8e9cbede188df19cd81" +
        "e642594185569c0966e022fe7a1f5d193f76df1bd21c304f19bff4e31c8aeeb986939b275bcf6d0ff7f69af6" +
        "230a7b5c96b6b1535e01eddc1d47b53d6aeac53c3dd7505b39bd8233c55bdadd42d64991e4ee941f435c88d1" +
        "93dbe91630bb1504836ffc9b7099ac782b1ddb9ec244363bd4cd7db34be2ad47b06dbb96ffcf316ce0f063b6" +
        "2bec781ad9d1059e8ef5913c9baa02db8f6b95932dc0d2e827e6e7f534069c6ae2e620b8149e4b6e9b6275f8" +
        "a9ffea2b61799bb08bf98c9d347aa0f0382b04a70f2a5b448531e39a2341c06388e9c449037903b1544e050a" +
        "382bd8694a245bfe64d25c9f7e119ddd2ecf7b641a11bcbd9f064a7fcfc77ac5635284ed058c95cfd401963b" +
        "2cbf55d1c7382c052b1772e87bff1906ea871fa6627e08797cce9b38abc97325934ab0c090571e6b069e18f5" +
        "c333333548ac83ef19e307e2433b6f02b3e7328cc3de0765df93ef3250b6e430d485da66694737888b8e0f19" +
        "a70b380dc37f6f7d8cad02ba514e611d734609b3166a584c06e5635b6367e5c992b1eb26221eb8f2a644ef07" +
        "52f1a2a38dfa310ac6ba4fe01bfb"
