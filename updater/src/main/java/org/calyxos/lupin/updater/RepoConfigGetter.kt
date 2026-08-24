/*
 * SPDX-FileCopyrightText: 2026 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepoConfigGetter @Inject constructor(@ApplicationContext context: Context) {

    val repoUrl = context.getString(R.string.repo_url)
    val repoCert = context.getString(R.string.repo_cert)

}
