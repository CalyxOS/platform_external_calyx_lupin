/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import android.app.Application
import android.content.res.Resources.getSystem
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.util.Log
import androidx.core.os.ConfigurationCompat.getLocales
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.calyxos.lupin.AppItemState.Selectable
import org.fdroid.index.v2.IndexV2
import java.util.concurrent.TimeUnit.MINUTES

private val TAG = MainViewModel::class.simpleName
private val TIMEOUT = MINUTES.toMillis(2)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repoManager = RepoManager(app.applicationContext)
    private val packageInstaller = PackageInstaller(getApplication())
    private val appInstaller = AppInstaller(packageInstaller)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // get the on-disk index and remember it
            val oldIndex = try {
                val result = repoManager.getLocalIndex()
                onLocalIndexLoaded(result)
                result.index
            } catch (e: Exception) {
                Log.e(TAG, "Error getting local index: ", e)
                return@launch
            }
            // update index from the internet
            if (isOnlineNotMetered()) {
                try {
                    onOnlineIndexLoaded(oldIndex, repoManager.getOnlineIndex())
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading index: ", e)
                }
            }
        }
    }

    /**
     * Populates the initial [state] with the list of apps from the local on-device repo.
     */
    private fun onLocalIndexLoaded(result: RepoResult) {
        val locales = getLocales(getSystem().configuration)
        val items = result.index.packages.map { (packageName, packageV2) ->
            AppItem(
                packageName = packageName,
                result = result,
                packageV2 = packageV2,
                locales = locales,
            )
        }.sortedBy { it.name }
        _state.value = UiState.SelectingApps(items, items.isNotEmpty())
    }

    /**
     * Updates the current [state] with the list of apps from the online repo
     * by merging both indexes together, preferring newer versions.
     * This runs on the UiThread to not have the list updated on our feet.
     */
    private suspend fun onOnlineIndexLoaded(
        oldIndex: IndexV2,
        result: RepoResult,
    ) = withContext(Dispatchers.Main) {
        // If we are past selecting apps, don't update anymore, it is too late now
        val s = state.value as? UiState.SelectingApps ?: return@withContext
        val locales = getLocales(getSystem().configuration)
        // Go through old items and update them if necessary, remembering package names we've seen
        val oldPackageNames = HashSet<String>(s.items.size)
        val updatedItems = s.items.map { item ->
            oldPackageNames.add(item.packageName)
            // get packages and versions codes
            val packageV2 = result.index.packages[item.packageName]
            val oldPackageV2 = oldIndex.packages[item.packageName]
            val versionCode = packageV2?.versions?.values?.first()?.versionCode ?: 0
            val oldVersionCode = oldPackageV2?.versions?.values?.first()?.versionCode ?: 0
            // update current item, of new version code is higher than old one
            if (versionCode > oldVersionCode) AppItem(
                item = item,
                result = result,
                packageV2 = packageV2 ?: oldPackageV2 ?: error("Not supposed to happen"),
                locales = locales,
            ) else item
        }
        // Add new apps that were not in the old index
        val newItems = result.index.packages.filter { (packageName, _) ->
            !oldPackageNames.contains(packageName)
        }.map { (packageName, packageV2) ->
            AppItem(
                packageName = packageName,
                result = result,
                packageV2 = packageV2,
                locales = locales,
            )
        }
        // sort new items and update state
        val items = (updatedItems + newItems).sortedBy { it.name }
        _state.value = UiState.SelectingApps(items, items.isNotEmpty())
    }

    private fun isOnlineNotMetered(): Boolean {
        val connectivityManager = app.getSystemService(ConnectivityManager::class.java)
        val currentNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
        return caps?.hasCapability(NET_CAPABILITY_NOT_METERED) ?: false
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
