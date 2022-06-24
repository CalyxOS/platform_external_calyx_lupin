/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin.ui

import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
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
import org.fdroid.lupin.AppItem
import org.fdroid.lupin.AppItemState
import org.fdroid.lupin.R
import org.fdroid.lupin.ui.theme.LupinTheme
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
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
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
            else -> {}
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

internal fun getRandomAppItem(context: Context) = AppItem(
    packageName = "org.example",
    icon = context.getDrawable(R.drawable.ic_launcher_foreground)!!,
    name = LoremIpsum(3).values.first(),
    summary = LoremIpsum(8).values.first(),
    state = when {
        Random.nextBoolean() -> AppItemState.Selectable(Random.nextBoolean())
        Random.nextBoolean() -> AppItemState.Progress
        else -> AppItemState.Success
    },
)
