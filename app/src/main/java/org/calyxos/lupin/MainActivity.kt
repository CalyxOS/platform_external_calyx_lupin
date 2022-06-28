/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import org.calyxos.lupin.ui.InstallPage
import org.calyxos.lupin.ui.RESULT_NEXT
import org.calyxos.lupin.ui.RESULT_SKIP
import org.calyxos.lupin.ui.theme.LupinTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LupinTheme {
                InstallPage(
                    state = viewModel.state.collectAsState(),
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
        if (viewModel.state.value is UiState.Done) {
            setResult(RESULT_NEXT)
            finishAfterTransition()
        } else {
            viewModel.onNextClicked()
        }
    }
}
