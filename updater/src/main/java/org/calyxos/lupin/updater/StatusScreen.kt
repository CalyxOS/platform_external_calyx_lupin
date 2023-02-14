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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.calyxos.lupin.updater.ui.theme.LupinTheme
import kotlin.random.Random

@Composable
fun StatusScreen(
    paddingValues: PaddingValues,
    lastCheckedMillis: Long,
    onCheckButtonClicked: () -> Unit,
) {
    val settingsPadding = 24.dp // taken from system settings
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
        val lastChecked = formatDate(LocalContext.current, lastCheckedMillis)
        Text(
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            text = stringResource(R.string.status_screen_last_check, lastChecked),
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onCheckButtonClicked,
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
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
fun StatusScreenPreview() {
    LupinTheme {
        Surface {
            StatusScreen(PaddingValues(0.dp), System.currentTimeMillis() - Random.nextLong()) {}
        }
    }
}
