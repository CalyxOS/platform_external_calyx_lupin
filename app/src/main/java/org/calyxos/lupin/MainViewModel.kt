/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.app.Application
import android.content.res.Resources.getSystem
import androidx.core.os.ConfigurationCompat.getLocales
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.calyxos.lupin.AppItemState.Selectable
import org.fdroid.LocaleChooser.getBestLocale
import org.fdroid.index.v2.IndexV2
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repoReader = RepoReader()
    private val packageInstaller = PackageInstaller(getApplication())

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repoReader.readRepo().collect {
                onIndexLoaded(it)
            }
        }
    }

    private fun onIndexLoaded(index: IndexV2) {
        val locales = getLocales(getSystem().configuration)
        val items = index.packages.map { app ->
            val metadata = app.value.metadata
            val version = app.value.versions.values.first()
            AppItem(
                packageName = app.key,
                icon = "$REPO_PATH/${metadata.icon.getBestLocale(locales)?.name}",
                name = metadata.name.getBestLocale(locales) ?: "Unknown",
                summary = metadata.summary.getBestLocale(locales) ?: "",
                apk = File(REPO_PATH, version.file.name),
                state = Selectable(true),
            )
        }.sortedBy { it.name }
        _state.value = UiState.SelectingApps(items, items.isNotEmpty())
    }

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
                    val result = packageInstaller.install(item.packageName, item.apk)
                    val newItem = if (result.success) {
                        item.copy(state = AppItemState.Success)
                    } else {
                        item.copy(state = AppItemState.Error)
                    }
                    done++
                    replaceItem(items, i, newItem, done, total)
                } else {
                    replaceItem(items, i, item.copy(state = AppItemState.ShowOnly), done, total)
                }
            }
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

}
