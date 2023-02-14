/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.app.Application
import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val TAG = MainViewModel::class.simpleName

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val repoManager: RepoManager,
    app: Application,
) : AndroidViewModel(app) {

    private val _lastCheckedMillis = MutableStateFlow(settingsManager.lastCheckedMillis)
    val lastCheckedMillis = _lastCheckedMillis.asStateFlow()

    @UiThread
    fun onCheckButtonClicked() = viewModelScope.launch {
        // TODO move this into a WorkManager job
        val index = repoManager.downloadIndex() ?: return@launch

        // update last checked
        val now = System.currentTimeMillis()
        settingsManager.lastCheckedMillis = now
        _lastCheckedMillis.value = now

        // update apps from repo, if index is new
        if (index.repo.timestamp > settingsManager.lastRepoTimestamp) {
            val allUpdated = repoManager.updateApps(index)
            if (allUpdated) settingsManager.lastRepoTimestamp = index.repo.timestamp
        } else {
            Log.d(TAG, "Repo was not updated")
        }
    }
}
