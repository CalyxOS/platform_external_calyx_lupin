/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin

import android.graphics.drawable.Drawable

sealed class AppItemState {
    object ShowOnly : AppItemState()
    class Selectable(val selected: Boolean) : AppItemState() {
        fun invert() = Selectable(!selected)
    }

    object Progress : AppItemState()
    object Success : AppItemState()
    object Error : AppItemState()
}

data class AppItem(
    val packageName: String,
    val icon: Drawable,
    val name: String,
    val summary: String,
    val state: AppItemState,
)
