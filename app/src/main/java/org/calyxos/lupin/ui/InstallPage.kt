/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.calyxos.lupin.AppItem
import org.calyxos.lupin.R
import org.calyxos.lupin.UiState
import org.calyxos.lupin.UiState.SelectingApps
import org.calyxos.lupin.ui.theme.LupinTheme

@Composable
fun InstallPage(
    state: State<UiState>,
    skipClickListener: (() -> Unit),
    nextClickListener: (() -> Unit),
    itemClickListener: ((AppItem) -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            val listState = rememberLazyListState()
            LazyColumn(Modifier.weight(1f), listState) {
                listHeader(state)
                items(state.value.items) { item ->
                    AppItemRow(
                        item = item,
                        modifier = Modifier.padding(horizontal = horizontalMargin),
                        clickListener = if (state.value is SelectingApps) {
                            itemClickListener
                        } else null,
                    )
                }
            }
            ButtonRow(state, listState, skipClickListener, nextClickListener)
        }
    }
}

fun LazyListScope.listHeader(state: State<UiState>) {
    item {
        SuwHeader(
            icon = R.drawable.ic_launcher_foreground,
            title = stringResource(R.string.install_page_title),
            subtitle = stringResource(R.string.install_page_subtitle),
            modifier = Modifier
                .suwPageModifier()
                .padding(bottom = 8.dp)
        )
    }
    if (state.value is UiState.InstallingApps || state.value is UiState.Done) {
        val progress = if (state.value is UiState.InstallingApps) {
            val installing = state.value as UiState.InstallingApps
            installing.done.toFloat() / installing.total
        } else 1f
        item {
            val progressAnimation by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
            )
            LinearProgressIndicator(
                progress = progressAnimation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalMargin)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
fun ButtonRow(
    state: State<UiState>,
    listState: LazyListState,
    skipClickListener: () -> Unit,
    nextClickListener: () -> Unit,
) {
    Row(Modifier.padding(horizontal = horizontalMargin, vertical = 8.dp)) {
        val skipEnabled = state.value !is UiState.InstallingApps
        TextButton(enabled = skipEnabled, onClick = skipClickListener) {
            Text(text = stringResource(R.string.skip))
        }
        Spacer(modifier = Modifier.weight(1f))
        val isEnd = snapshotFlow {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastIndex == lastVisibleIndex
        }.collectAsState(initial = false)
        val coroutineScope = rememberCoroutineScope()
        val buttonEnabled = state.value is SelectingApps || state.value is UiState.Done
        val showMoreButton = state.value is SelectingApps && !isEnd.value
        Button(shape = MaterialTheme.shapes.large, enabled = buttonEnabled, onClick = {
            if (showMoreButton) coroutineScope.launch {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.last()
                listState.animateScrollToItem(lastVisible.index)
            } else {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
                nextClickListener()
            }
        }) {
            val textRes = if (showMoreButton) R.string.more else {
                val showInstall = (state.value as? SelectingApps)?.hasSelected ?: false
                if (showInstall) R.string.install else R.string.next
            }
            Text(text = stringResource(textRes))
        }
    }
}

@Composable
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
fun InstallPagePreview() {
    LupinTheme {
        val context = LocalContext.current
        val state = remember {
            mutableStateOf(UiState.InstallingApps(listOf(
                getRandomAppItem(context),
                getRandomAppItem(context),
                getRandomAppItem(context)
            ), 1, 3))
        }
        InstallPage(state, {}, {})
    }
}
