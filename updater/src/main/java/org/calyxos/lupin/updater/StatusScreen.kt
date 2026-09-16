/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.updater

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo.State
import org.calyxos.lupin.updater.ui.theme.LupinTheme
import kotlin.random.Random

@Composable
fun StatusScreen(
    paddingValues: PaddingValues,
    lastCheckedMillis: Long,
    isCheckRunning: Boolean,
    checkState: State?,
    showSuccess: Boolean? = null,
    onCheckButtonClicked: () -> Unit,
) {
    val settingsPadding = 24.dp // taken from system settings
    var showSuccess by remember { mutableStateOf(showSuccess) }
    LaunchedEffect(checkState) {
        // checkState also shows past successes, so only show success after worker ran while open
        if (checkState == State.RUNNING) showSuccess = false
        if (checkState == State.SUCCEEDED && showSuccess == false) showSuccess = true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(settingsPadding)
            .padding(paddingValues),
        verticalArrangement = spacedBy(settingsPadding),
    ) {
        Text(
            modifier = Modifier.padding(vertical = settingsPadding), // extra padding for heading
            style = MaterialTheme.typography.displaySmall,
            text = stringResource(R.string.status_screen_title),
        )
        if (isCheckRunning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (showSuccess == true && checkState == State.SUCCEEDED) {
            // TODO localize
            Text("Congratulations! All apps are up to date \uD83C\uDF89")
        }
        val lastChecked = formatDate(LocalContext.current, lastCheckedMillis)
        Text(
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            text = stringResource(R.string.status_screen_last_check, lastChecked),
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onCheckButtonClicked,
            enabled = !isCheckRunning,
            modifier = Modifier.align(End),
        ) {
            Text(
                text = stringResource(R.string.status_screen_check_button),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun Preview() {
    LupinTheme {
        Surface {
            StatusScreen(
                PaddingValues(0.dp),
                System.currentTimeMillis() - Random.nextLong(),
                false,
                checkState = State.SUCCEEDED,
            ) {}
        }
    }
}

@Composable
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
private fun DarkRunningPreview() {
    LupinTheme {
        Surface {
            StatusScreen(
                PaddingValues(0.dp),
                System.currentTimeMillis() - Random.nextLong(),
                true,
                checkState = State.RUNNING,
            ) {}
        }
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewSuccess() {
    LupinTheme {
        Surface {
            StatusScreen(
                PaddingValues(0.dp),
                System.currentTimeMillis() - Random.nextLong(),
                false,
                checkState = State.SUCCEEDED,
                showSuccess = true,
            ) {}
        }
    }
}
