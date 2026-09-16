/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.app.Application
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mu.KotlinLogging
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsManager: SettingsManager,
    app: Application,
) : AndroidViewModel(app) {

    private val log = KotlinLogging.logger {}
    private val workManager = WorkManager.getInstance(getApplication())

    val lastCheckedMillis = settingsManager.lastCheckedMillisState
    val checkStateFlow = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_MANUAL).map {
        it.getOrNull(0)?.state
    }
    val isCheckRunningFlow = checkStateFlow.map {
        it == WorkInfo.State.RUNNING
    }

    init {
        viewModelScope.launch {
            checkStateFlow.collect {
                if (it != null) {
                    log.info { "Work info: $it" }
                } else {
                    log.info { "No work info found" }
                }
            }
        }
    }

    @UiThread
    fun onCheckButtonClicked() = viewModelScope.launch {
        val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME_MANUAL, KEEP, workRequest)
    }
}
