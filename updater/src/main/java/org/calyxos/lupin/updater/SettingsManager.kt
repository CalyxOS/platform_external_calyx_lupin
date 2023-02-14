/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREF_NAME = "updater"
private const val PREF_LAST_CHECKED = "lastCheckedMillis"
private const val PREF_LAST_REPO_TIMESTAMP = "lastRepoTimestamp"

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * The timestamp when we last checked for updates.
     * Warning: Setting this value happens asynchronously
     * and is not immediately reflected by the getter.
     */
    var lastCheckedMillis: Long
        get() {
            return sharedPref.getLong(PREF_LAST_CHECKED, -1)
        }
        set(value) {
            sharedPref.edit().putLong(PREF_LAST_CHECKED, value).apply()
        }

    /**
     * The repo timestamp of the last downloaded repo index.
     * Warning: Setting this value happens asynchronously
     * and is not immediately reflected by the getter.
     */
    var lastRepoTimestamp: Long
        get() {
            return sharedPref.getLong(PREF_LAST_REPO_TIMESTAMP, -1)
        }
        set(value) {
            sharedPref.edit().putLong(PREF_LAST_REPO_TIMESTAMP, value).apply()
        }

}
