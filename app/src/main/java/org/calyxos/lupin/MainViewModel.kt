/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.app.Application
import android.content.res.Resources.getSystem
import android.util.Log
import androidx.core.os.ConfigurationCompat.getLocales
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.calyxos.lupin.AppItemState.Selectable
import org.fdroid.LocaleChooser.getBestLocale
import org.fdroid.index.v2.IndexV2
import java.io.File
import java.util.concurrent.TimeUnit.MINUTES

private val TAG = MainViewModel::class.simpleName
private val TIMEOUT = MINUTES.toMillis(2)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repoReader = RepoReader()
    private val packageInstaller = PackageInstaller(getApplication())
    private val appInstaller = AppInstaller(packageInstaller)

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

    fun onCheckAllClicked() {
        val s = state.value as? UiState.SelectingApps ?: return
        val allSelected = s.items.all { it.state is Selectable && it.state.selected }
        if (allSelected) {
            // unselect all
            val newItems = s.items.map {
                if (it.state is Selectable && it.state.selected) {
                    it.copy(state = it.state.invert())
                } else it
            }
            _state.value = UiState.SelectingApps(newItems, false)
        } else {
            // select all
            val newItems = s.items.map {
                if (it.state is Selectable && !it.state.selected) {
                    it.copy(state = it.state.invert())
                } else it
            }
            _state.value = UiState.SelectingApps(newItems, true)
        }
    }

    // this assumes that items don't change anymore once this is called
    fun onNextClicked() {
        val items = state.value.items.toMutableList()
        _state.value = UiState.SelectionComplete(items)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(TIMEOUT) {
                    appInstaller.installApps(items).collect { uiState ->
                        _state.value = uiState
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timed out installing apps", e)
                onTimeout(items)
            }
        }
    }

    private fun onTimeout(items: MutableList<AppItem>) {
        val newItems = items.map { item ->
            if (item.state is AppItemState.Progress || item.state is Selectable) {
                item.copy(state = AppItemState.Error)
            } else item
        }
        _state.value = UiState.Done(newItems)
    }

}
