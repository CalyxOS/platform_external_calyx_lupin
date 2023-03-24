/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
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
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.calyxos.lupin.InstallResult
import org.calyxos.lupin.STATUS_WAITING_FOR_USER_ACTION
import org.calyxos.lupin.getRequest
import org.fdroid.UpdateChecker
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.Entry
import org.fdroid.index.v2.EntryFileV2
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
    private val notificationManager: NotificationManager = mockk()
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
            notificationManager = notificationManager,
            coroutineContext = testDispatcher,
        )
    }

    @Test
    fun testDownloadEntry() = runTest {
        val indexFile = FileV2("entry.jar")
        val request = indexFile.getRequest(REPO_URL)
        // TODO this would need a tempfile injecter to be able to mock downloaded data

        every { context.cacheDir } returns tempDir
        coEvery { httpManager.get(request, receiver = any()) } just Runs

        updateManager.downloadEntry()
    }

    @Test
    fun testDownloadIndex() = runTest {
        val entry = Entry(
            timestamp = 42L,
            version = 23L,
            index = EntryFileV2(
                name = "index-v2.json",
                sha256 = "foo bar",
                size = 1337L,
                numPackages = 13,
            ),
        )
        val request = entry.index.getRequest(REPO_URL)
        // TODO this would need a tempfile injecter to be able to mock downloaded data

        every { context.cacheDir } returns tempDir
        coEvery { httpManager.get(request, receiver = any()) } just Runs

        updateManager.downloadIndex(entry)
    }

    @Test
    fun updateAppsDoesNothingForEmptyRepo() = runTest {
        val index = IndexV2(repo)

        every { installManager.clearUpOldSession() } just Runs

        assertFalse(updateManager.updateApps(index))
    }

    @Test
    fun singleAppHasNoUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), null)

        every { installManager.clearUpOldSession() } just Runs

        assertFalse(updateManager.updateApps(index))
    }

    @Test
    fun singleAppHasActiveSession() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery { installManager.hasActiveSession(packageName) } returns true

        // user needs to become active, because an active session is still waiting for confirmation
        every { notificationManager.showUserConfirmationRequiredNotification() } just Runs

        assertFalse(updateManager.updateApps(index))

        verify {
            notificationManager.showUserConfirmationRequiredNotification()
        }
        // don't clean up sessions when we still wait for user
        verify(exactly = 0) {
            installManager.clearUpOldSession()
        }
    }

    @Test
    fun singleAppIsShowingUserConfirmationDialog() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery { installManager.hasActiveSession(packageName) } returns false

        // our app is in foreground, so we have shown the install dialog to the user
        coEvery {
            installManager.installUpdate(packageName, packageVersion)
        } returns InstallResult(STATUS_WAITING_FOR_USER_ACTION, null)

        // no retry needed
        assertFalse(updateManager.updateApps(index))

        // don't clean up sessions when we still wait for user and don't show notification
        verify(exactly = 0) {
            installManager.clearUpOldSession()
            notificationManager.showUserConfirmationRequiredNotification()
        }
    }

    @Test
    fun appIsNotInstalled() = runTest {
        every {
            packageManager.getPackageInfo(packageName, GET_SIGNATURES)
        } throws NameNotFoundException()
        every { installManager.clearUpOldSession() } just Runs

        assertFalse(updateManager.updateApps(index))
    }

    @Test
    fun appFailsToDownloadUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery { installManager.hasActiveSession(packageName) } returns false
        coEvery { installManager.installUpdate(packageName, packageVersion) } throws Exception()
        coEvery { installManager.hasActiveSession(packageName) } returns false
        every { installManager.clearUpOldSession() } just Runs

        // try this again when checking updates next
        assertTrue(updateManager.updateApps(index))
    }

    @Test
    fun appFailsToInstallUpdate() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery { installManager.hasActiveSession(packageName) } returns false
        coEvery {
            installManager.installUpdate(packageName, packageVersion)
        } returns InstallResult(Exception())
        every { installManager.clearUpOldSession() } just Runs

        // no need to try again
        assertFalse(updateManager.updateApps(index))

        coVerify {
            installManager.installUpdate(packageName, packageVersion)
        }
    }

    @Test
    fun appNeedsUserConfirmation() = runTest {
        expectGetUpdate(packageName, listOf(packageVersion), packageVersion)
        coEvery { installManager.hasActiveSession(packageName) } returns false
        coEvery {
            installManager.installUpdate(packageName, packageVersion)
        } returns InstallResult(STATUS_PENDING_USER_ACTION, "click OK please!")
        // user needs to become active, because an active session is still waiting for confirmation
        every { notificationManager.showUserConfirmationRequiredNotification() } just Runs
        every { installManager.clearUpOldSession() } just Runs

        // no need to try again
        assertFalse(updateManager.updateApps(index))

        coVerify {
            installManager.installUpdate(packageName, packageVersion)
        }
        verify {
            notificationManager.showUserConfirmationRequiredNotification()
        }
        // don't clean up sessions when we still wait for user
        verify(exactly = 0) {
            installManager.clearUpOldSession()
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
        coEvery { installManager.hasActiveSession(packageName) } returns false
        expectSuccessfulInstall(packageName, packageVersion)

        expectGetUpdate(packageName2, listOf(pv), pv)
        coEvery { installManager.hasActiveSession(packageName2) } returns false
        expectSuccessfulInstall(packageName2, pv)

        coEvery { installManager.clearUpOldSession() } just Runs

        assertFalse(updateManager.updateApps(index))

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
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_WEBVIEW) } returns false
        // check if trichrome was updated to that already, it wasn't, but later it is
        expectTriChromeVersionCode(pvWebview.versionCode - 1, pvChrome.versionCode)
        // get suggested version for trichrome, download and install it
        every {
            updateChecker.getSuggestedVersion(listOf(pvTriChromeLib), null)
        } returns pvTriChromeLib
        expectSuccessfulInstall(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)

        // only now you can install webview
        expectSuccessfulInstall(PACKAGE_NAME_WEBVIEW, pvWebview)

        coEvery { installManager.hasActiveSession(PACKAGE_NAME_TRICHROME_LIB) } returns false
        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now install update for chrome, no need to install trichrome lib anymore
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_CHROME) } returns false
        // trichrome was updated now
        expectSuccessfulInstall(PACKAGE_NAME_CHROME, pvChrome)

        coEvery { installManager.clearUpOldSession() } just Runs

        assertFalse(updateManager.updateApps(triChromeIndex))

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
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_WEBVIEW) } returns false
        // check if trichrome was updated to that already, it wasn't
        expectTriChromeVersionCode(packageVersion.versionCode - 1)
        every { installManager.clearUpOldSession() } just Runs

        // we need a repo update to fix missing trichrome, so need to try again now
        assertFalse(updateManager.updateApps(index))
    }

    @Test
    fun triChromeLibraryFailsToInstall() = runTest {
        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(pvWebview), pvWebview)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_WEBVIEW) } returns false
        // trichrome can be not installed at all, or needs an update
        val triChromeVersionCode = if (Random.nextBoolean()) pvWebview.versionCode - 1 else null
        // check if trichrome was updated to that already, it wasn't
        expectTriChromeVersionCode(triChromeVersionCode)
        // get suggested version for trichrome, download and install it
        every {
            updateChecker.getSuggestedVersion(listOf(pvTriChromeLib), null)
        } returns pvTriChromeLib
        // trichrome install fails
        coEvery {
            installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)
        } returns InstallResult(7, "failed to install")

        coEvery { installManager.hasActiveSession(PACKAGE_NAME_TRICHROME_LIB) } returns false
        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now try to install update for chrome
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_CHROME) } returns false
        // chrome runs into same issue, so can't install anything
        every { installManager.clearUpOldSession() } just Runs

        // we need a repo update to fix missing trichrome, so need to try again now
        assertFalse(updateManager.updateApps(triChromeIndex))

        // verify that we did not try to install webview and chrome
        coVerify(exactly = 0) {
            installManager.installUpdate(PACKAGE_NAME_WEBVIEW, pvWebview)
            installManager.installUpdate(PACKAGE_NAME_CHROME, pvChrome)
        }
    }

    @Test
    fun triChromeLibraryNeedsUserConfirmation() = runTest {
        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(pvWebview), pvWebview)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_WEBVIEW) } returns false
        // check if trichrome was updated to that already
        // trichrome needs an update
        expectTriChromeVersionCode(pvWebview.versionCode - 1)
        // get suggested version for trichrome, download and install it
        every {
            updateChecker.getSuggestedVersion(listOf(pvTriChromeLib), null)
        } returns pvTriChromeLib
        // trichrome install needs user confirmation
        coEvery {
            installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)
        } returns InstallResult(STATUS_PENDING_USER_ACTION, null)

        coEvery { installManager.hasActiveSession(PACKAGE_NAME_TRICHROME_LIB) } returns false
        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now try to install update for chrome
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_CHROME) } returns false
        // chrome runs into same issue, so can't install anything
        every { installManager.clearUpOldSession() } just Runs

        // user needs to become active for trichrome
        every { notificationManager.showUserConfirmationRequiredNotification() } just Runs

        // try again soon, so we can install webview and chrome when trichrome lib is hopefully in
        assertTrue(updateManager.updateApps(triChromeIndex))

        // verify that we did not try to install webview and chrome
        coVerify(exactly = 0) {
            installManager.installUpdate(PACKAGE_NAME_WEBVIEW, pvWebview)
            installManager.installUpdate(PACKAGE_NAME_CHROME, pvChrome)
        }
        // trichrome was installed
        coVerify {
            installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)
        }
        // user got notified
        verify {
            notificationManager.showUserConfirmationRequiredNotification()
        }
    }

    @Test
    fun triChromeLibraryHasActiveSession() = runTest {
        // webview has one update
        expectGetUpdate(PACKAGE_NAME_WEBVIEW, listOf(pvWebview), pvWebview)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_WEBVIEW) } returns false
        // check if trichrome was updated to that already
        // trichrome needs an update
        expectTriChromeVersionCode(pvWebview.versionCode - 1)
        // it has an active session still
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_TRICHROME_LIB) } returns true

        // trichrome is never reported as installed
        every {
            packageManager.getPackageInfo(PACKAGE_NAME_TRICHROME_LIB, GET_SIGNATURES)
        } throws NameNotFoundException()

        // now try to install update for chrome
        expectGetUpdate(PACKAGE_NAME_CHROME, listOf(pvChrome), pvChrome)
        coEvery { installManager.hasActiveSession(PACKAGE_NAME_CHROME) } returns false
        // chrome runs into same issue, so can't install anything
        every { installManager.clearUpOldSession() } just Runs

        // user needs to become active for trichrome
        every { notificationManager.showUserConfirmationRequiredNotification() } just Runs

        // try again soon, so we can install webview and chrome when trichrome lib is hopefully in
        assertTrue(updateManager.updateApps(triChromeIndex))

        // verify that we did not try to install trichrome lib, webview or chrome
        coVerify(exactly = 0) {
            installManager.installUpdate(PACKAGE_NAME_WEBVIEW, pvWebview)
            installManager.installUpdate(PACKAGE_NAME_TRICHROME_LIB, pvTriChromeLib)
            installManager.installUpdate(PACKAGE_NAME_CHROME, pvChrome)
        }
        // user got notified
        verify {
            notificationManager.showUserConfirmationRequiredNotification()
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
