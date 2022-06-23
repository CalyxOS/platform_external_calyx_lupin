/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin

import android.graphics.drawable.Drawable

data class AppItem(
    val icon: Drawable,
    val name: String,
    val summary: String,
    val selected: Boolean,
)
