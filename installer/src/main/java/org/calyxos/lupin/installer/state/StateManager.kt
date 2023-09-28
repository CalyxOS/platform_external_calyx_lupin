/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.state

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calyxos.lupin.installer.install.AppInstaller
import org.calyxos.lupin.installer.install.AppInstallerService
import javax.inject.Inject
import javax.inject.Singleton

private val TAG = StateManager::class.simpleName

@Singleton
class StateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repoManager: RepoManager,
    private val appInstaller: AppInstaller,
    private val scope: CoroutineScope,
) : OnlineStateChangedListener {

    val networkManager = NetworkManager(context, this)

    private var onlineIndexLoaded: Boolean = true // set to false to enable loading of online index

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

    private val alwaysInstallItems = ArrayList<AppItem>()

    init {
        // update indexes
        scope.launch {
            // get the on-disk index and remember it
            try {
                onLocalIndexLoaded(repoManager.getLocalIndex())
            } catch (e: Exception) {
                Log.e(TAG, "Error getting local index: ", e)
                return@launch
            }
            if (!onlineIndexLoaded && networkManager.onlineState.value) loadOnlineIndex()
        }
    }

    private suspend fun loadOnlineIndex() {
        // set this early to prevent double loading via [onOnlineStateChanged]
        onlineIndexLoaded = true
        // update index from the internet
        val onlineIndex = try {
            repoManager.getOnlineIndex()
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading index: ", e)
            // allow the index to be downloaded again
            onlineIndexLoaded = false
            return
        }
        onOnlineIndexLoaded(onlineIndex)
    }

    override fun onOnlineStateChanged(online: Boolean) {
        // load online index, if we have not done so already
        if (online && !onlineIndexLoaded) scope.launch {
            loadOnlineIndex()
        }
    }

    /**
     * Populates the initial [state] with the list of apps from the local on-device repo.
     */
    private fun onLocalIndexLoaded(result: RepoResult) {
        Log.i(TAG, "onLocalIndexLoaded")
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        val items = result.index.packages.mapNotNull { (packageName, packageV2) ->
            val signer = packageV2.getSigner()
            if (signer == null || signer.sha256.isEmpty()) {
                Log.w(TAG, "App had no signer: $packageName")
                return@mapNotNull null
            } else {
                val item = AppItem(
                    packageName = packageName,
                    result = result,
                    packageV2 = packageV2,
                    locales = locales,
                )
                // only include items we are not installing always anyway
                // otherwise we need to handle/prevent clicks and inverting selection state
                if (item.isAlwaysInstall) {
                    alwaysInstallItems.add(item) // remember items we want to always install
                    return@mapNotNull null
                } else if (item.isHidden) {
                    return@mapNotNull null
                } else {
                    return@mapNotNull item
                }
            }
        }.sortedBy { it.name }
        // FIXME: compute real boolean for hasSelected (maybe none are selected by default)
        _state.value = UiState.SelectingApps(items, hasSelected = items.isNotEmpty())
    }

    /**
     * Updates the current [state] with the list of apps from the online repo
     * by merging both indexes together, preferring newer versions.
     * This runs on the UiThread to not have the list updated on our feet.
     */
    private suspend fun onOnlineIndexLoaded(result: RepoResult) = withContext(Dispatchers.Main) {
        Log.i(TAG, "onOnlineIndexLoaded")
        networkManager.stopObservingNetworkState()
        // If we are past selecting apps, don't update anymore, it is too late now
        val s = state.value as? UiState.SelectingApps ?: return@withContext
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        // Go through old items and update them if necessary, remembering package names we've seen
        val updatedItems = s.items.map { item ->
            getUpdatedItem(result, item, locales)
        }.sortedBy { it.name }
        // Go through always-to-install items and update them if necessary
        alwaysInstallItems.replaceAll { item ->
            getUpdatedItem(result, item, locales)
        }
        // FIXME: compute real boolean for hasSelected (maybe none are selected by default)
        _state.value = UiState.SelectingApps(updatedItems, hasSelected = updatedItems.isNotEmpty())
    }

    private fun getUpdatedItem(
        result: RepoResult,
        item: AppItem,
        locales: LocaleListCompat,
    ): AppItem {
        // get packages and versions codes
        val packageV2 = result.index.packages[item.packageName] ?: return item
        // update current item, if new version code is higher than old one
        return if (item.isValidUpdate(packageV2)) AppItem(
            item = item,
            result = result,
            packageV2 = packageV2,
            locales = locales,
        ) else item
    }

    fun onItemClicked(item: AppItem) {
        val newItems = state.value.items.map { oldItem ->
            if (oldItem == item && oldItem.state is AppItemState.Selectable) {
                oldItem.copy(state = oldItem.state.invert())
            } else {
                oldItem
            }
        }
        val hasSelected =
            newItems.find { it.state is AppItemState.Selectable && it.state.selected } != null
        _state.value = UiState.SelectingApps(newItems, hasSelected)
    }

    fun onCheckAllClicked() {
        val s = state.value as? UiState.SelectingApps ?: return
        val allSelected = s.items.all { it.state is AppItemState.Selectable && it.state.selected }
        if (allSelected) {
            // unselect all
            val newItems = s.items.map {
                if (it.state is AppItemState.Selectable && it.state.selected) {
                    it.copy(state = it.state.invert())
                } else it
            }
            _state.value = UiState.SelectingApps(newItems, false)
        } else {
            // select all
            val newItems = s.items.map {
                if (it.state is AppItemState.Selectable && !it.state.selected) {
                    it.copy(state = it.state.invert())
                } else it
            }
            _state.value = UiState.SelectingApps(newItems, true)
        }
    }

    /**
     * The user has clicked the next button.
     * This assumes that items don't change anymore once this is called
     * @return true, if we will install some apps and false, if there's nothing to install.
     */
    fun onNextClicked(): Boolean {
        val items = (state.value.items + alwaysInstallItems).toMutableList()
        _state.value = UiState.SelectionComplete(items)

        // don't start service if there's nothing to install with it
        val hasNoSelectedApps = items.find {
            it.state is AppItemState.Selectable && it.state.selected
        } == null
        if (hasNoSelectedApps) {
            _state.value = UiState.Done(items)
            return false
        }

        AppInstallerService.start(context)
        scope.launch(Dispatchers.IO) {
            appInstaller.installApps(items)
            Log.i(TAG, "Stopping AppInstallerService...")
            AppInstallerService.stop(context)
        }
        scope.launch(Dispatchers.IO) {
            appInstaller.uiState.collect { uiState ->
                _state.value = uiState
            }
        }
        return true
    }

    /**
     * The user has clicked the skip button.
     * This assumes that items don't change anymore once this is called.
     */
    fun onSkipClicked() {
        // unselect all, so we don't install anything the user didn't want to install
        val newItems = state.value.items.map {
            if (it.state is AppItemState.Selectable && it.state.selected) {
                it.copy(state = it.state.invert())
            } else it
        }
        _state.value = UiState.SelectingApps(newItems, false)

        // now do the same as clicking next, so that always-to-install apps get installed
        onNextClicked()
    }

}
