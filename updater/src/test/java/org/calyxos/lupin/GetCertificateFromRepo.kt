/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import org.calyxos.lupin.updater.REPO_URL
import org.fdroid.download.HttpDownloaderV2
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.EntryVerifier
import org.fdroid.index.v2.FileV2
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class GetCertificateFromRepo {
    @field:TempDir
    lateinit var tempDir: File

    // Change this to any URL you want to get the cert from
    private val repoUrl = REPO_URL

    @Test
    fun foo() {
        val indexFile = FileV2("entry.jar")
        val request = indexFile.getRequest(repoUrl)
        val file = File(tempDir, "entry.jar")
        assertTrue(file.createNewFile())

        val httpManager = HttpManager("Lupin test")
        HttpDownloaderV2(httpManager, request, file).download()
        val verifier = EntryVerifier(file, null, null)
        val (cert, _) = verifier.getStreamAndVerify { inputStream ->
            inputStream.readBytes()
        }
        assertTrue(cert.isNotEmpty())
        println("CERT:")
        println(cert)
    }
}
