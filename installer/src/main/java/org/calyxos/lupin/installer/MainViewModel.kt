/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.calyxos.lupin.installer.state.AppItem
import org.calyxos.lupin.installer.state.StateManager
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

    /**
     * @return true, if we should stay on this page and false, if we can close it.
     */
    fun onNextClicked(): Boolean = stateManager.onNextClicked()

    fun onSkipClicked() = stateManager.onSkipClicked()
}
