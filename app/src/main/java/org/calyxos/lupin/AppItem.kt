/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import java.io.File

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
    val icon: Any?,
    val name: String,
    val summary: String,
    val apk: File,
    val state: AppItemState,
)
