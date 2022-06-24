/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.fdroid.lupin

import android.app.Application
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fdroid.lupin.AppItemState.Selectable

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val list = listOf(
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
        getItem(),
    )
    private val _state = MutableStateFlow<UiState>(UiState.SelectingApps(list, true))
    val state = _state.asStateFlow()

    fun onItemClicked(item: AppItem) {
        val newItems = state.value.items.map {
            if (it == item && it.state is Selectable) it.copy(state = it.state.invert()) else it
        }
        val hasSelected = newItems.find { it.state is Selectable && it.state.selected } != null
        _state.value = UiState.SelectingApps(newItems, hasSelected)
    }

    // this assumes that items don't change anymore once this is called
    fun onNextClicked() {
        val items = state.value.items.toMutableList()
        _state.value = UiState.SelectionComplete(items)
        viewModelScope.launch(Dispatchers.IO) {
            var done = 0
            val total = items.count { it.state is Selectable && it.state.selected }
            items.forEachIndexed { i, item ->
                if (item.state is Selectable && item.state.selected) {
                    replaceItem(items, i, item.copy(state = AppItemState.Progress), done, total)
                    delay(1000)
                    done++
                    replaceItem(items, i, item.copy(state = AppItemState.Success), done, total)
                } else {
                    replaceItem(items, i, item.copy(state = AppItemState.ShowOnly), done, total)
                }
            }
            _state.value = UiState.InstallingApps(items, items.size, items.size)
            delay(500)
            _state.value = UiState.Done(items)
        }
    }

    private fun replaceItem(
        items: MutableList<AppItem>,
        index: Int,
        item: AppItem,
        done: Int,
        total: Int,
    ) {
        _state.value = UiState.InstallingApps(items.apply { set(index, item) }, done, total)
    }

    private fun getItem() = AppItem(
        packageName = "org.example",
        icon = getApplication<Application>().getDrawable(R.drawable.ic_launcher_foreground)!!,
        name = LoremIpsum(3).values.first(),
        summary = LoremIpsum(8).values.first(),
        state = Selectable(true),
    )

}
