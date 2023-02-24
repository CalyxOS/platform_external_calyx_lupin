/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */
package org.calyxos.lupin.updater.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.calyxos.lupin.updater.InstallManager
import org.calyxos.lupin.updater.R
import org.calyxos.lupin.updater.ui.theme.LupinTheme
import javax.inject.Inject

@AndroidEntryPoint
class ConfirmationActivity : ComponentActivity() {

    @Inject
    lateinit var installManager: InstallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            lifecycle.whenResumed {
                installManager.onUserAttentionReceived(this@ConfirmationActivity)
            }
        }
        // content not super important as we finish this usually before it becomes visible
        setContent {
            LupinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val settingsPadding = 24.dp // taken from system settings
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(settingsPadding),
                        verticalArrangement = spacedBy(settingsPadding),
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = settingsPadding),
                            style = MaterialTheme.typography.displaySmall,
                            text = stringResource(R.string.notification_confirmation_title),
                        )
                    }
                }
            }
        }
    }
}
