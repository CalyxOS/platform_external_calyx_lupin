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
import org.calyxos.lupin.TempFileProvider
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private const val CERT_TEST = "308202cf308201b7a0030201020204410b599a300d06092a864886f70d01010b05003018311630140603550403130d546f727374656e2047726f7465301e170d3134303631363139303332305a170d3431313130313139303332305a3018311630140603550403130d546f727374656e2047726f746530820122300d06092a864886f70d01010105000382010f003082010a02820101009fee536211eb53d0b054b0b2cf72fe4ba66f341b5b93730f8fe4a4a68b105a35a3a5daf5b54443d744bb19eb954456e6fb1f1fcfe9023684cddb0643be2d70a1a7a37e75badad62ba607e238a8d88fb1601d46030824ef5e719b65f855801ee323ac68f8da7afea30d9366c1a132e1cab21dcf218d163a5aa8dcc5b31d876085414fcf0eed74bc5a02c7d297beeaa756843a0acaf31eec9969322c8695ee9f2be84e58347b47dc81e429a6f11e5cb1415aea54b88a1911a7fc62fbd53ea7a72b1e26e7da8111510dc98631e939760095441ca2d0a6b316527dbe146245cf279607f3c9ff7006a1adf367b8fe55a7c3a9bdb66aebbe9b71711981e0b342dca8730203010001a321301f301d0603551d0e04160414649492d14e97d5937667ee2e555926899f9a2610300d06092a864886f70d01010b050003820101002bb228f5b31e68a9175f2a6cbb0d727991fea7b71fbb295aaa28963963b5c697d20929b57e299c9607d20ac332d86544300de7d1cf4602162d9929fbb7465be279a44a31cb06f778d66625077d615affc751a300843bad116fcee9c958b88aef0f25988dc63d7f8853517d738efd9888e61f395597090ae7b41a5983e8d2b4bd74ee98c9a3dab91114f43b7336cc00889385567e0f717aa76526dbdae2fa34e007375b2db3d34c423b77b37774b93eff762c4b3b4fb05f8b26256570607a1400cddad2ebd4762bcf4efe703248fa5b9ab455e3a5c98cb46f10adb6979aed8f96a688fd1d2a3beab380308e2ebe0a4a880615567aafc0bfe344c5d7ef677e060f"

@Suppress("DEPRECATION")
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerTest {

    private val context: Context = mockk()
    private val httpManager: HttpManager = mockk()
    private val updateChecker: UpdateChecker = mockk()
    private val installManager: InstallManager = mockk()
    private val notificationManager: NotificationManager = mockk()
    private val tempFileProvider: TempFileProvider = mockk()
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
            certificate = CERT_TEST,
            tempFileProvider = tempFileProvider,
            coroutineContext = testDispatcher,
        )
    }

    @Test
    fun testDownloadEntry() = runTest {
        val indexFile = FileV2("entry.jar")
        val request = indexFile.getRequest(REPO_URL)
        val jarFile = File.createTempFile("test", null, tempDir)

        every { tempFileProvider.createTempFile("entry", ".jar") } returns jarFile
        coEvery { httpManager.get(request, receiver = any()) }  answers {
            File("src/test/resources/entry.jar").copyTo(jarFile, overwrite = true)
        }

        val entryV2 = updateManager.downloadEntry() ?: fail("no entry")
        assertEquals(1686317269000, entryV2.timestamp)
        assertEquals(20002, entryV2.version)
        assertEquals("c1707b6aa0764340c93ca0c54dddbb0a6638f5cb53247cbdab8dfbc409d02371", entryV2.index.sha256)
        assertEquals(1, entryV2.diffs.size)
    }

    @Test
    fun testDownloadIndex() = runTest {
        val entry = Entry(
            timestamp = 42L,
            version = 23L,
            index = EntryFileV2(
                name = "index-v2.json",
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                size = 1337L,
                numPackages = 13,
            ),
        )
        val request = entry.index.getRequest(REPO_URL)
        val jsonFile = File.createTempFile("test", null, tempDir)
        // changing index data needs changing sha256 hash above
        val indexJson = """
{
    "repo": {
        "name": {"en-US": "Testing Repo"},
        "description": {"en-US": "many apps"},
        "icon": {"en-US": {"name": "/icons/fdroid-icon.png", "sha256": "686ba750aac96c47398c7247eb949d80882650de63f4a6b60f3ca1efa5528934", "size": 3367}},
        "address": "https://example.org/fdroid/repo",
        "timestamp": 1686317269000
    }
}       """

        every { tempFileProvider.createTempFile("index-v2-", ".json") } returns jsonFile
        coEvery { httpManager.get(request, receiver = any()) } answers {
            jsonFile.outputStream().use { outputStream ->
                outputStream.write(indexJson.toByteArray())
            }
        }

        val index = updateManager.downloadIndex(entry) ?: fail("No index")
        assertEquals("Testing Repo", index.repo.name["en-US"])
        assertEquals("https://example.org/fdroid/repo", index.repo.address)
        assertEquals(1686317269000, index.repo.timestamp)
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
