/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.state

sealed class UiState(
    val items: List<AppItem>,
    val showSkipButton: Boolean = false,
    val canTapNextButton: Boolean = false,
) {

    object Loading : UiState(emptyList(), showSkipButton = true)

    class SelectingApps(items: List<AppItem>, val hasSelected: Boolean) :
        UiState(items, showSkipButton = true, canTapNextButton = true)

    class SelectionComplete(items: List<AppItem>) : UiState(items, canTapNextButton = true)

    class InstallingApps(items: List<AppItem>, val done: Long, val total: Long) :
        UiState(items, canTapNextButton = true)

    class Done(items: List<AppItem>) : UiState(items, canTapNextButton = true)

}
