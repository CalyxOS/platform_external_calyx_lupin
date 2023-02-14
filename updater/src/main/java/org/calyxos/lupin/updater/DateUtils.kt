/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.Context
import android.text.format.DateUtils.DAY_IN_MILLIS
import android.text.format.DateUtils.FORMAT_ABBREV_MONTH
import android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
import android.text.format.DateUtils.FORMAT_ABBREV_TIME
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.text.format.DateUtils.WEEK_IN_MILLIS
import android.text.format.DateUtils.getRelativeDateTimeString
import android.text.format.DateUtils.getRelativeTimeSpanString

fun formatDate(ctx: Context, time: Long): String {
    if (time < 0) return ctx.getString(R.string.never)
    val now = System.currentTimeMillis()
    val diff = now - time
    if (diff < MINUTE_IN_MILLIS) return ctx.getString(R.string.now)

    val flags =
        FORMAT_ABBREV_RELATIVE or FORMAT_SHOW_DATE or FORMAT_ABBREV_TIME or FORMAT_ABBREV_MONTH
    return if (diff in DAY_IN_MILLIS until WEEK_IN_MILLIS) {
        // also show time when older than a day, but newer than a week
        getRelativeDateTimeString(ctx, time, MINUTE_IN_MILLIS, WEEK_IN_MILLIS, flags).toString()
    } else getRelativeTimeSpanString(time, now, MINUTE_IN_MILLIS, flags).toString()
}
