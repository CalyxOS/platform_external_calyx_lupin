/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.calyxos.lupin.installer.R
import org.calyxos.lupin.installer.state.AppItem
import org.calyxos.lupin.installer.state.AppItemState.Selectable
import org.calyxos.lupin.installer.state.UiState
import org.calyxos.lupin.installer.state.UiState.SelectingApps
import org.calyxos.lupin.installer.ui.theme.LupinTheme

@Composable
fun InstallPage(
    state: UiState,
    isOnline: Boolean,
    onCheckAllClicked: () -> Unit,
    skipClickListener: (() -> Unit),
    nextClickListener: (() -> Unit),
    itemClickListener: ((AppItem) -> Unit)? = null,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val listState = rememberLazyListState()
            LazyColumn(Modifier.weight(1f), listState) {
                listHeader(state)
                if (state is SelectingApps) {
                    allCheckbox(
                        isChecked = state.items.all { it.state is Selectable && it.state.selected },
                        onCheckAllClicked = onCheckAllClicked,
                    )
                }
                items(state.items) { item ->
                    AppItemRow(
                        modifier = Modifier.padding(horizontal = horizontalMargin),
                        item = item,
                        isOnline = isOnline,
                        clickListener = if (state is SelectingApps) {
                            itemClickListener
                        } else null,
                    )
                }
            }
            ButtonRow(state, listState, skipClickListener, nextClickListener)
        }
    }
}

fun LazyListScope.listHeader(state: UiState) {
    item {
        SuwHeader(
            icon = R.drawable.fdroid_logo,
            title = stringResource(R.string.install_page_title),
            subtitle = stringResource(R.string.install_page_subtitle),
            modifier = Modifier
                .suwPageModifier()
                .padding(bottom = 8.dp)
        )
    }
    if (state is UiState.InstallingApps || state is UiState.Done) {
        val progress = if (state is UiState.InstallingApps) {
            state.done.toFloat() / state.total
        } else 1f
        item {
            val progressAnimation by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            LinearProgressIndicator(
                progress = { progressAnimation },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalMargin)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

fun LazyListScope.allCheckbox(
    isChecked: Boolean,
    onCheckAllClicked: () -> Unit,
) {
    item {
        Column {
            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = horizontalMargin)
                    .padding(top = 8.dp)
            )
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCheckAllClicked() }
                    .padding(horizontal = horizontalMargin)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.install_page_all),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .align(CenterVertically)
                ) {
                    Checkbox(
                        modifier = Modifier.align(Alignment.Center),
                        checked = isChecked,
                        onCheckedChange = null,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = horizontalMargin)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun ButtonRow(
    state: UiState,
    listState: LazyListState,
    skipClickListener: () -> Unit,
    nextClickListener: () -> Unit,
) {
    Row(Modifier.padding(horizontal = horizontalMargin + 3.dp, vertical = 19.dp)) {
        if (state.showSkipButton) TextButton(onClick = skipClickListener) {
            Text(text = stringResource(R.string.skip))
        }
        Spacer(modifier = Modifier.weight(1f))
        val isEnd = snapshotFlow {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastIndex == lastVisibleIndex
        }.collectAsState(initial = false)
        val coroutineScope = rememberCoroutineScope()
        val showMoreButton = state is SelectingApps && !isEnd.value
        Button(
            modifier = Modifier.heightIn(min = 44.dp),
            shape = MaterialTheme.shapes.large,
            enabled = state.canTapNextButton,
            onClick = {
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
                val showInstall = (state as? SelectingApps)?.hasSelected ?: false
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
            mutableStateOf(
                UiState.InstallingApps(
                    listOf(
                        getRandomAppItem(context),
                        getRandomAppItem(context),
                        getRandomAppItem(context)
                    ), 1, 3
                )
            )
        }
        InstallPage(state.value, true, {}, {}, {})
    }
}
