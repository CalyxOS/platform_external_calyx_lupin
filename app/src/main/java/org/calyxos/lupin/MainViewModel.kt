/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.calyxos.lupin.state.AppItem
import org.calyxos.lupin.state.StateManager
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val stateManager: StateManager,
) : AndroidViewModel(app) {

    val state = stateManager.state
    val onlineState = stateManager.networkManager.onlineState

    fun onItemClicked(item: AppItem) = stateManager.onItemClicked(item)
    fun onCheckAllClicked() = stateManager.onCheckAllClicked()
    fun onNextClicked() = stateManager.onNextClicked()
}
