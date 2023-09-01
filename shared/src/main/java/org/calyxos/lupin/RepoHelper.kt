/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.content.Context
import androidx.annotation.WorkerThread
import org.fdroid.IndexFile
import org.fdroid.download.DownloadRequest
import org.fdroid.download.HttpDownloaderV2
import org.fdroid.download.HttpManager
import org.fdroid.download.Mirror
import org.fdroid.index.IndexConverter
import org.fdroid.index.IndexParser
import org.fdroid.index.parseV1
import org.fdroid.index.v1.IndexV1Verifier
import org.fdroid.index.v2.FileV2
import org.fdroid.index.v2.IndexV2
import java.io.File

object RepoHelper {

    private const val REPO_INDEX = "index-v1.jar"

    @WorkerThread
    fun downloadIndex(
        context: Context,
        repoUrl: String,
        cert: String,
        httpManager: HttpManager
    ): IndexV2 {
        val file = File.createTempFile("index-v1-", ".jar", context.cacheDir)
        return try {
            val indexFile = FileV2(REPO_INDEX)
            val request = indexFile.getRequest(repoUrl)
            HttpDownloaderV2(httpManager, request, file).download()
            getIndex(file, cert)
        } finally {
            file.delete()
        }
    }

    @WorkerThread
    fun getIndex(file: File, cert: String): IndexV2 {
        val verifier = IndexV1Verifier(file, cert, null)
        val (_, index) = verifier.getStreamAndVerify { inputStream ->
            IndexParser.parseV1(inputStream)
        }
        return IndexConverter().toIndexV2(index)
    }
}

fun IndexFile.getRequest(url: String): DownloadRequest {
    return DownloadRequest(this, listOf(Mirror(url)))
}
