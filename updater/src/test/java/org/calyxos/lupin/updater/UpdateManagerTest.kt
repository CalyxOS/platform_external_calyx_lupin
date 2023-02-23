/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNATURES
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.SharedLibraryInfo
import android.content.pm.VersionedPackage
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.getRequest
import org.fdroid.UpdateChecker
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.FileV2
import org.fdroid.index.v2.IndexV2
import org.fdroid.index.v2.PackageVersionV2
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerTest {

    private val context: Context = mockk()
    private val httpManager: HttpManager = mockk()
    private val updateChecker: UpdateChecker = mockk()
    private val installManager: InstallManager = mockk()
    private val updateManager: UpdateManager

    private val packageManager: PackageManager = mockk()
    private val packageInfo: PackageInfo = mockk()

    @field:TempDir
    lateinit var tempDir: File
    private val testDispatcher = Dispatchers.Unconfined

    init {
        every { context.packageManager } returns packageManager
        updateManager = UpdateManager(
            context = context,
            httpManager = httpManager,
            updateChecker = updateChecker,
            installManager = installManager,
            coroutineContext = testDispatcher,
        )
    }

    @Test
    fun testDownloadIndex() = runTest {
        val indexFile = FileV2("index-v1.jar")
        val request = indexFile.getRequest(REPO_URL)
        // TODO this would need a tempfile injecter to be able to mock downloaded data

        every { context.cacheDir } returns tempDir
        coEvery { httpManager.get(request, receiver = any()) } just Runs

        updateManager.downloadIndex()
    }

    @Test
    fun updateAppsDoesNothingForEmptyRepo() = runTest {
        val index = IndexV2(repo)

        assertFalse(updateManager.updateApps(index).retry)
    }

    @Test
    fun singleAppHasNoUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), null)

        assertFalse(updateManager.updateApps(index).retry)
    }

    @Test
    fun appIsNotInstalled() = runTest {
        every {
            packageManager.getPackageInfo(packageName, GET_SIGNATURES)
        } throws NameNotFoundException()

        assertFalse(updateManager.updateApps(index).retry)
    }

    @Test
    fun appFailsToDownloadUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery { installManager.installUpdate(packageName, packageVersion) } throws Exception()

        // try this again when checking updates next
        assertTrue(updateManager.updateApps(index).retry)
    }

    @Test
    fun appFailsToInstallUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery {
            installManager.installUpdate(packageName, packageVersion)
        } returns InstallResult(Exception())

        // no need to try again
        assertFalse(updateManager.updateApps(index).retry)

        coVerify {
            installManager.installUpdate(packageName, packageVersion)
        }
    }

    @Test
    fun twoAppsInstallUpdates() = runTest {
        val packageName2 = "net.example.app"
        val pv = getPackageVersionV2("file2")
        val pv2 = getPackageV2(pv)
        val index = IndexV2(
            repo = repo,
            packages = mapOf(packageName to packageV2, packageName2 to pv2)
        )

        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        expectSuccessfulInstall(packageName, packageVersion)

        expectGetUpdate(packageName2, listOf(pv), pv)
        expectSuccessfulInstall(packageName2, pv)

        assertFalse(updateManager.updateApps(index).retry)

        // verify both apps got installed
        coVerify {
            installManager.installUpdate(packageName, packageVersion)
            installManager.installUpdate(packageName2, pv)
        }
    }

    @Test
    fun triChromeLibraryGetsInstalledBeforeWebViewUpdate() = runTest {
        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(pvWebview), pvWebview)
        // check if trichrome was updated to that already, it wasn't, but later it is
        expectTriChromeVersionCode(pvWebview.versionCode - 1, pvChrome.versionCode)
        // get suggested version for trichrome, download and install it
        every {
            updateChecker.getSuggestedVersion(listOf(pvTriChromeLib), null)
        } returns pvTriChromeLib
        expectSuccessfulInstall(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)

        // only now you can install webview
        expectSuccessfulInstall(PACKAGE_NAME_WEBVIEW, pvWebview)

        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now install update for chrome, no need to install trichrome lib anymore
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        // trichrome was updated now
        expectSuccessfulInstall(PACKAGE_NAME_CHROME, pvChrome)

        assertFalse(updateManager.updateApps(triChromeIndex).retry)

        // ensure that packages got processed in expected order
        coVerifyOrder {
            packageManager.getPackageInfo(PACKAGE_NAME_WEBVIEW, GET_SIGNATURES)
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
            packageManager.getPackageInfo(PACKAGE_NAME_CHROME, GET_SIGNATURES)
        }
        // ensure that trichrome lib got installed before both updates
        coVerifyOrder {
            installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)
            installManager.installUpdate(PACKAGE_NAME_WEBVIEW, pvWebview)
            installManager.installUpdate(PACKAGE_NAME_CHROME, pvChrome)
        }
    }

    @Test
    fun triChromeLibraryNotInIndex() = runTest {
        val index = IndexV2(
            repo = repo,
            packages = mapOf(PACKAGE_NAME_WEBVIEW to packageV2)
        )

        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(packageVersion), packageVersion)
        // check if trichrome was updated to that already, it wasn't
        expectTriChromeVersionCode(packageVersion.versionCode - 1)

        // we need a repo update to fix missing trichrome, so need to try again now
        assertFalse(updateManager.updateApps(index).retry)
    }

    @Test
    fun triChromeLibraryFailsToInstall() = runTest {
        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(pvWebview), pvWebview)
        // trichrome can be not installed at all, or needs an update
        val triChromeVersionCode = if (Random.nextBoolean()) pvWebview.versionCode - 1 else null
        // check if trichrome was updated to that already, it wasn't, but later it is
        expectTriChromeVersionCode(triChromeVersionCode)
        // get suggested version for trichrome, download and install it
        every {
            updateChecker.getSuggestedVersion(listOf(pvTriChromeLib), null)
        } returns pvTriChromeLib
        // trichrome install fails
        coEvery {
            installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)
        } returns InstallResult(7, "failed to install")

        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now try to install update for chrome
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        // chrome runs into same issue, so can't install anything

        // we need a repo update to fix missing trichrome, so need to try again now
        assertFalse(updateManager.updateApps(triChromeIndex).retry)

        // verify that we did not try to install webview and chrome
        coVerify(exactly = 0) {
            installManager.installUpdate(PACKAGE_NAME_WEBVIEW, pvWebview)
            installManager.installUpdate(PACKAGE_NAME_CHROME, pvChrome)
        }
    }

    private fun expectGetUpdate(
        packageName: String,
        versions: List<PackageVersionV2>,
        update: PackageVersionV2?,
    ) {
        every { packageManager.getPackageInfo(packageName, GET_SIGNATURES) } returns packageInfo
        every { updateChecker.getUpdate(versions, packageInfo) } returns update
    }

    private fun expectSuccessfulInstall(packageName: String, update: PackageVersionV2) {
        coEvery {
            installManager.installUpdate(packageName, update)
        } returns InstallResult(STATUS_SUCCESS, null)
    }

    private fun expectTriChromeVersionCode(versionCode: Long?, versionCode2: Long? = null) {
        val sharedLibraryInfo: SharedLibraryInfo = mockk()
        every {
            packageManager.getSharedLibraries(any<PackageInfoFlags>())
        } returns listOf(sharedLibraryInfo)
        if (versionCode == null) {
            every { sharedLibraryInfo.declaringPackage } returns VersionedPackage("foo bar", 1)
        } else {
            every {
                sharedLibraryInfo.declaringPackage
            } returns
                VersionedPackage(PACKAGE_NAME_TRICHROME_LIB, versionCode) andThen
                VersionedPackage(PACKAGE_NAME_TRICHROME_LIB, versionCode - 1)
            if (versionCode2 == null) {
                every { sharedLibraryInfo.longVersion } returns versionCode
            } else {
                every { sharedLibraryInfo.longVersion } returns versionCode andThen versionCode2
            }
        }
    }

}
