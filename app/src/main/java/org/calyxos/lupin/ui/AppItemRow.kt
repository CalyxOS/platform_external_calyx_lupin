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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calyxos.lupin.R
import org.calyxos.lupin.state.AppItem
import org.calyxos.lupin.state.AppItemState
import org.calyxos.lupin.ui.theme.LupinTheme
import org.fdroid.index.v2.SignerV2
import java.io.File
import kotlin.random.Random

@Composable
fun AppItemRow(
    item: AppItem,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    clickListener: ((AppItem) -> Unit)? = null,
) {
    val canBeInstalled = isOnline || !item.isOnlineOnly
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .run {
                if (clickListener != null && canBeInstalled) {
                    clickable { clickListener(item) }
                } else this
            }
            .then(modifier),
    ) {
        AsyncImage(
            model = item.icon,
            fallback = painterResource(R.drawable.ic_default),
            error = painterResource(R.drawable.ic_default),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            alignment = Center,
            alpha = if (canBeInstalled) DefaultAlpha else ContentAlpha.disabled,
            contentScale = ContentScale.Fit,
        )
        Column(Modifier
            .weight(1f)
            .padding(horizontal = 16.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.body1,
                color = if (canBeInstalled) Color.Unspecified else {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (canBeInstalled) item.summary else {
                    stringResource(R.string.install_page_offline)
                },
                style = MaterialTheme.typography.caption,
                color = if (canBeInstalled) Color.Unspecified else {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        ItemState(item.state, canBeInstalled)
    }
}

@Composable
fun RowScope.ItemState(state: AppItemState, canBeSelected: Boolean = true) {
    Box(Modifier
        .size(32.dp)
        .align(CenterVertically)) {
        when (state) {
            is AppItemState.Selectable -> {
                Checkbox(
                    modifier = Modifier.align(Center),
                    checked = canBeSelected && state.selected,
                    enabled = canBeSelected,
                    onCheckedChange = null,
                )
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
            AppItemRow(
                item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.Progress),
                isOnline = true,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreview() {
    AppItemRow(
        item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.ShowOnly),
        isOnline = true,
    )
}

@Composable
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
fun AppItemRowPreviewOffline() {
    LupinTheme {
        Surface {
            AppItemRow(
                item = getRandomAppItem(LocalContext.current).copy(
                    isOnlineOnly = true,
                    state = AppItemState.Selectable(false)
                ),
                isOnline = false,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreviewSuccess() {
    AppItemRow(
        item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.Success),
        isOnline = true,
    )
}

@Composable
@Preview(showBackground = true)
fun AppItemRowPreviewError() {
    AppItemRow(
        item = getRandomAppItem(LocalContext.current).copy(state = AppItemState.Error),
        isOnline = true,
    )
}

internal fun getRandomAppItem(context: Context) = AppItem(
    packageName = "org.example",
    versionCode = 42L,
    icon = context.getDrawable(R.drawable.ic_launcher_foreground)!!,
    name = LoremIpsum(3).values.first(),
    summary = LoremIpsum(8).values.first(),
    apkGetter = { File("/") },
    apkSize = 42,
    signers = SignerV2(emptyList()),
    isOnlineOnly = Random.nextBoolean(),
    state = when {
        Random.nextBoolean() -> AppItemState.Selectable(Random.nextBoolean())
        Random.nextBoolean() -> AppItemState.Progress
        else -> AppItemState.Success
    },
)
