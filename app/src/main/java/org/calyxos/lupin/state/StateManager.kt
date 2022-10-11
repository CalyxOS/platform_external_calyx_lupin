package org.calyxos.lupin.state

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calyxos.lupin.install.AppInstaller
import org.calyxos.lupin.install.AppInstallerService
import org.fdroid.index.v2.IndexV2
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

    private var localIndex: IndexV2? = null

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        // update indexes
        scope.launch {
            // get the on-disk index and remember it
            val oldIndex = try {
                val result = repoManager.getLocalIndex()
                onLocalIndexLoaded(result)
                result.index
            } catch (e: Exception) {
                Log.e(TAG, "Error getting local index: ", e)
                return@launch
            }
            if (networkManager.onlineState.value) {
                loadOnlineIndex(oldIndex)
            } else {
                // save local index in case we come online later
                localIndex = oldIndex
            }
        }
    }

    private suspend fun loadOnlineIndex(oldIndex: IndexV2) {
        // update index from the internet
        try {
            onOnlineIndexLoaded(oldIndex, repoManager.getOnlineIndex())
            localIndex = null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading index: ", e)
        }
    }

    override fun onOnlineStateChanged(online: Boolean) {
        // load online index, if we have not done so already
        val oldIndex = localIndex
        if (online && oldIndex != null) scope.launch {
            loadOnlineIndex(oldIndex)
        }
    }

    /**
     * Populates the initial [state] with the list of apps from the local on-device repo.
     */
    private fun onLocalIndexLoaded(result: RepoResult) {
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
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
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
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
            if (item.isOnlineOnly || versionCode > oldVersionCode) AppItem(
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

    fun onItemClicked(item: AppItem) {
        val newItems = state.value.items.map {
            if (it == item && it.state is AppItemState.Selectable) it.copy(state = it.state.invert()) else it
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

    // this assumes that items don't change anymore once this is called
    fun onNextClicked() {
        val items = state.value.items.toMutableList()
        _state.value = UiState.SelectionComplete(items)

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
    }

}
