/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.calyxos.lupin.installer.ui.InstallPage
import org.calyxos.lupin.installer.ui.RESULT_NEXT
import org.calyxos.lupin.installer.ui.RESULT_SKIP
import org.calyxos.lupin.installer.ui.theme.LupinTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LupinTheme {
                InstallPage(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    isOnline = viewModel.onlineState.collectAsStateWithLifecycle().value,
                    onCheckAllClicked = viewModel::onCheckAllClicked,
                    skipClickListener = this::onSkipClicked,
                    nextClickListener = this::onNextClicked,
                    itemClickListener = viewModel::onItemClicked,
                )
            }
        }
    }

    private fun onSkipClicked() {
        setResult(RESULT_SKIP)
        finishAfterTransition()
    }

    private fun onNextClicked() {
        if (viewModel.state.value.showSkipButton) {
            // if there's nothing to install, it is the same as skip
            if (!viewModel.onNextClicked()) skip()
        } else {
            // when skipping isn't possible anymore, next was already clicked, so we finish here
            skip()
        }
    }

    private fun skip() {
        setResult(RESULT_NEXT)
        finishAfterTransition()
    }
}
