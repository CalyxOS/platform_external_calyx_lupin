/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.ui

import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calyxos.lupin.AppItem
import org.calyxos.lupin.AppItemState
import org.calyxos.lupin.R
import org.calyxos.lupin.ui.theme.LupinTheme
import java.io.File
import kotlin.random.Random

@Composable
fun AppItemRow(
    item: AppItem,
    modifier: Modifier = Modifier,
    clickListener: ((AppItem) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .run {
                if (clickListener != null) {
                    clickable { clickListener(item) }
                } else this
            }
            .then(modifier),
    ) {
        AsyncImage(
            model = item.icon,
            placeholder = painterResource(R.drawable.ic_launcher_foreground),
            fallback = painterResource(android.R.drawable.ic_dialog_alert),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            alignment = Alignment.Center,
            contentScale = ContentScale.Inside,
        )
        Column(Modifier
            .weight(1f)
            .padding(horizontal = 8.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = item.summary,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        ItemState(item.state)
    }
}

@Composable
fun ItemState(state: AppItemState) {
    Box(Modifier.size(32.dp)) {
        when (state) {
            is AppItemState.Selectable -> {
                Checkbox(checked = state.selected, onCheckedChange = null)
            }
            is AppItemState.Progress -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            is AppItemState.Success -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = stringResource(id = R.string.installed),
                    tint = MaterialTheme.colors.primary,
                )
            }
            is AppItemState.Error -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_error),
                    contentDescription = stringResource(id = R.string.error_not_installed),
                    tint = MaterialTheme.colors.primary,
                )
            }
            is AppItemState.ShowOnly -> {}
        }
    }
}

@Composable
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
fun AppItemRowPreviewNight() {
    LupinTheme {
        Surface {
            AppItemRow(item = getRandomAppItem(LocalContext.current)
                .copy(state = AppItemState.Progress))
        }
    }
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreview() {
    AppItemRow(item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.ShowOnly))
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreviewSuccess() {
    AppItemRow(item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.Success))
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreviewError() {
    AppItemRow(item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.Error))
}

internal fun getRandomAppItem(context: Context) = AppItem(
    packageName = "org.example",
    icon = context.getDrawable(R.drawable.ic_launcher_foreground)!!,
    name = LoremIpsum(3).values.first(),
    summary = LoremIpsum(8).values.first(),
    apk = File("/"),
    state = when {
        Random.nextBoolean() -> AppItemState.Selectable(Random.nextBoolean())
        Random.nextBoolean() -> AppItemState.Progress
        else -> AppItemState.Success
    },
)
