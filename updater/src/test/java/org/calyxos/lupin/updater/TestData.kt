/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import org.fdroid.index.v2.FileV1
import org.fdroid.index.v2.IndexV2
import org.fdroid.index.v2.ManifestV2
import org.fdroid.index.v2.MetadataV2
import org.fdroid.index.v2.PackageV2
import org.fdroid.index.v2.PackageVersionV2
import org.fdroid.index.v2.RepoV2
import kotlin.random.Random

val repo = RepoV2(
    address = "https://example.org",
    timestamp = Random.nextLong(),
)
const val packageName = "app.example.org"
val packageVersion = getPackageVersionV2("file1")
val packageV2 = getPackageV2(packageVersion)
val index = IndexV2(
    repo = repo,
    packages = mapOf(packageName to packageV2)
)

fun getPackageV2(packageVersion: PackageVersionV2) = PackageV2(
    metadata = MetadataV2(
        added = Random.nextLong(),
        lastUpdated = Random.nextLong(),
    ),
    versions = mapOf("foo" to packageVersion)
)

fun getPackageVersionV2(fileName: String) = PackageVersionV2(
    added = Random.nextLong(),
    file = FileV1(
        name = fileName,
        sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // 0 bytes
        size = Random.nextLong(23, Long.MAX_VALUE),
    ),
    manifest = ManifestV2(
        versionName = "1.0",
        versionCode = 10,
    )
)
