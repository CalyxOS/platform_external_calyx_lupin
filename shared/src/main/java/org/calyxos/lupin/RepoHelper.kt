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
import org.fdroid.index.IndexParser
import org.fdroid.index.parseEntry
import org.fdroid.index.parseV2
import org.fdroid.index.v2.Entry
import org.fdroid.index.v2.EntryVerifier
import org.fdroid.index.v2.FileV2
import org.fdroid.index.v2.IndexV2
import java.io.File

object RepoHelper {

    private const val REPO_ENTRY = "entry.jar"

    @WorkerThread
    fun downloadEntry(
        context: Context,
        repoUrl: String,
        cert: String,
        httpManager: HttpManager,
    ): Entry {
        val file = File.createTempFile("entry", ".jar", context.cacheDir)
        return try {
            val entryFile = FileV2(REPO_ENTRY)
            val request = entryFile.getRequest(repoUrl)
            HttpDownloaderV2(httpManager, request, file).download()
            getEntry(file, cert)
        } finally {
            file.delete()
        }
    }

    /**
     * Use this, if you always need to download the full index, no matter if it has changed or not.
     */
    @WorkerThread
    fun downloadIndex(
        context: Context,
        repoUrl: String,
        cert: String,
        httpManager: HttpManager,
    ): IndexV2 {
        val entryFile = File.createTempFile("entry", ".jar", context.cacheDir)
        val indexFile = File.createTempFile("index-v2-", ".json", context.cacheDir)
        return try {
            // download and verify entry
            val entryRequest = FileV2(REPO_ENTRY).getRequest(repoUrl)
            HttpDownloaderV2(httpManager, entryRequest, entryFile).download()
            val entry = getEntry(entryFile, cert)

            // download index no matter what the entry says
            val indexRequest = entry.index.getRequest(repoUrl)
            HttpDownloaderV2(httpManager, indexRequest, indexFile).download()
            getIndex(indexFile)
        } finally {
            entryFile.delete()
            indexFile.delete()
        }
    }

    @WorkerThread
    fun getEntry(file: File, cert: String): Entry {
        val verifier = EntryVerifier(file, cert, null)
        val (_, index) = verifier.getStreamAndVerify { inputStream ->
            IndexParser.parseEntry(inputStream)
        }
        return index
    }

    @WorkerThread
    fun getIndex(file: File): IndexV2 {
        return file.inputStream().use { inputStream ->
            IndexParser.parseV2(inputStream)
        }
    }
}

fun IndexFile.getRequest(url: String): DownloadRequest {
    return DownloadRequest(this, listOf(Mirror(url)))
}
