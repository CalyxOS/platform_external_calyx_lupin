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
import org.calyxos.lupin.PackageInstaller
import org.calyxos.lupin.getRequest
import org.fdroid.IndexFile
import org.fdroid.UpdateChecker
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.FileV2
import org.fdroid.index.v2.IndexV2
import org.fdroid.index.v2.PackageVersionV2
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RepoManagerTest {

    private val context: Context = mockk()
    private val httpManager: HttpManager = mockk()
    private val updateChecker: UpdateChecker = mockk()
    private val packageInstaller: PackageInstaller = mockk()
    private val repoManager: RepoManager

    private val packageManager: PackageManager = mockk()
    private val packageInfo: PackageInfo = mockk()

    @field:TempDir
    lateinit var tempDir: File
    private val testDispatcher = Dispatchers.Unconfined

    init {
        every { context.packageManager } returns packageManager
        repoManager = RepoManager(
            context = context,
            httpManager = httpManager,
            updateChecker = updateChecker,
            packageInstaller = packageInstaller,
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

        repoManager.downloadIndex()
    }

    @Test
    fun updateAppsDoesNothingForEmptyRepo() = runTest {
        val index = IndexV2(repo)

        assertTrue(repoManager.updateApps(index))
    }

    @Test
    fun singleAppHasNoUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), null)

        assertTrue(repoManager.updateApps(index))
    }

    @Test
    fun appIsNotInstalled() = runTest {
        every {
            packageManager.getPackageInfo(packageName, GET_SIGNATURES)
        } throws NameNotFoundException()

        assertTrue(repoManager.updateApps(index))
    }

    @Test
    fun appFailsToDownloadUpdate() = runTest {
        val request = packageVersion.file.getRequest(REPO_URL)

        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        every { context.cacheDir } returns tempDir
        coEvery { httpManager.get(request, receiver = any()) } throws Exception()

        // try this again when checking updates next
        assertFalse(repoManager.updateApps(index))
    }

    @Test
    fun appFailsToInstallUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        expectDownload(packageVersion.file)
        coEvery {
            packageInstaller.install(packageName, any(), any())
        } returns InstallResult(Exception())

        // no need to try again
        assertTrue(repoManager.updateApps(index))

        coVerify {
            packageInstaller.install(packageName, any(), any())
        }
    }

    @Test
    fun twoAppsInstallUpdates() = runTest {
        val packageName2 = "net.example.app"
        val pv = getPackageVersionV2("file1")
        val index = IndexV2(
            repo = repo,
            packages = mapOf(packageName to packageV2, packageName2 to getPackageV2(pv))
        )

        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        expectDownload(packageVersion.file)
        expectSuccessfulInstall(packageName)

        expectGetUpdate(packageName2, listOf(pv), pv)
        expectDownload(pv.file)
        expectSuccessfulInstall(packageName2)

        assertTrue(repoManager.updateApps(index))

        // verify both apps got installed
        coVerify {
            packageInstaller.install(packageName, any(), any())
            packageInstaller.install(packageName2, any(), any())
        }
    }

    @Test
    fun triChromeLibraryGetsInstalledBeforeWebViewUpdate() = runTest {
        val pvWebview = getPackageVersionV2("webview")
        val pvTriChromeLib = getPackageVersionV2("trichromelib")
        val pvChrome = getPackageVersionV2("chrome")
        val index = IndexV2(
            repo = repo,
            packages = mapOf(
                PACKAGE_NAME_WEBVIEW to getPackageV2(pvWebview),
                PACKAGE_NAME_TRICHROME_LIB to getPackageV2(pvTriChromeLib),
                PACKAGE_NAME_CHROME to getPackageV2(pvChrome),
            )
        )

        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(pvWebview), pvWebview)
        // check if chrome was updated to that already
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_CHROME, PackageInfoFlags.of(0))
        } returns PackageInfo().apply {
            packageName = PACKAGE_NAME_CHROME
            longVersionCode = pvChrome.versionCode - 1 // chrome isn't updated
        }
        // get suggested version for trichrome, download and install it
        every {
            updateChecker.getSuggestedVersion(listOf(pvTriChromeLib), null)
        } returns pvTriChromeLib
        expectDownload(pvTriChromeLib.file)
        expectSuccessfulInstall(PACKAGE_NAME_TRICHROME_LIB)

        // only now you can install webview
        expectDownload(pvWebview.file)
        expectSuccessfulInstall(PACKAGE_NAME_WEBVIEW)

        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now install update for chrome, no need to install trichrome lib anymore
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        // check if webview was updated to that version of chrome
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_WEBVIEW, PackageInfoFlags.of(0))
        } returns PackageInfo().apply {
            packageName = PACKAGE_NAME_WEBVIEW
            longVersionCode = pvWebview.versionCode // webview was just updated
        }
        expectDownload(pvChrome.file)
        expectSuccessfulInstall(PACKAGE_NAME_CHROME)

        assertTrue(repoManager.updateApps(index))

        // ensure that packages got processed in expected order
        coVerifyOrder {
            packageManager.getPackageInfo(PACKAGE_NAME_WEBVIEW, GET_SIGNATURES)
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
            packageManager.getPackageInfo(PACKAGE_NAME_CHROME, GET_SIGNATURES)
        }
        // ensure that trichrome lib got installed before both updates
        coVerifyOrder {
            packageInstaller.install(PACKAGE_NAME_TRICHROME_LIB, any(), any())
            packageInstaller.install(PACKAGE_NAME_WEBVIEW, any(), any())
            packageInstaller.install(PACKAGE_NAME_CHROME, any(), any())
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
        // check if chrome was updated to that already
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_CHROME, PackageInfoFlags.of(0))
        } returns PackageInfo().apply {
            packageName = PACKAGE_NAME_CHROME
            longVersionCode = packageVersion.versionCode - 1 // chrome isn't updated
        }

        // we need a repo update to fix missing trichrome, so need to try again now
        assertTrue(repoManager.updateApps(index))
    }

    private fun expectGetUpdate(
        packageName: String,
        versions: List<PackageVersionV2>,
        update: PackageVersionV2?,
    ) {
        every { packageManager.getPackageInfo(packageName, GET_SIGNATURES) } returns packageInfo
        every { updateChecker.getUpdate(versions, packageInfo) } returns update
    }

    private fun expectDownload(indexFile: IndexFile) {
        val request = indexFile.getRequest(REPO_URL)
        every { context.cacheDir } returns tempDir
        coEvery { httpManager.get(request, receiver = any()) } just Runs
    }

    private fun expectSuccessfulInstall(packageName: String) {
        coEvery {
            packageInstaller.install(packageName, any(), any())
        } returns InstallResult(STATUS_SUCCESS, null)
    }

}
