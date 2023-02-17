/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.app.Application
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsManager: SettingsManager,
    app: Application,
) : AndroidViewModel(app) {

    private val workManager = WorkManager.getInstance(getApplication())

    val lastCheckedMillis = settingsManager.lastCheckedMillisState
    val checkLiveData = workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME_MANUAL).map {
        KotlinLogging.logger {  }.error { it } // TODO remove
        it.getOrNull(0)?.state?.isFinished ?: true
    }

    @UiThread
    fun onCheckButtonClicked() = viewModelScope.launch {
        val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME_MANUAL, KEEP, workRequest)
    }
}
