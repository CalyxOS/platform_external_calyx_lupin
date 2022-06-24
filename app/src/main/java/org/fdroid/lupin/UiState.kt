/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin

sealed class UiState(
    val items: List<AppItem>,
) {

    object Loading : UiState(emptyList())

    class SelectingApps(items: List<AppItem>, val hasSelected: Boolean) : UiState(items)

    class SelectionComplete(items: List<AppItem>) : UiState(items)

    class InstallingApps(items: List<AppItem>, val done: Int, val total: Int) : UiState(items)

    class Done(items: List<AppItem>) : UiState(items)

}
