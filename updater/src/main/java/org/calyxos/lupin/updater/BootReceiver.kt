/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import androidx.work.BackoffPolicy.EXPONENTIAL
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.NetworkType.UNMETERED
import androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest.Companion.DEFAULT_BACKOFF_DELAY_MILLIS
import mu.KotlinLogging
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.MINUTES

class BootReceiver : BroadcastReceiver() {

    private val log = KotlinLogging.logger {}

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOT_COMPLETED) return

        log.info { "Scheduling periodic update worker" }
        val workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            repeatInterval = 3,
            repeatIntervalTimeUnit = HOURS,
            flexTimeInterval = 30,
            flexTimeIntervalUnit = MINUTES
        ).setBackoffCriteria(
            backoffPolicy = EXPONENTIAL,
            backoffDelay = DEFAULT_BACKOFF_DELAY_MILLIS,
            timeUnit = MILLISECONDS,
        ).setInitialDelay(
            duration = 10,
            timeUnit = MINUTES,
        ).setConstraints(
            constraints = Constraints.Builder()
                .setRequiredNetworkType(UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
        ).setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, UPDATE, workRequest)
    }
}
