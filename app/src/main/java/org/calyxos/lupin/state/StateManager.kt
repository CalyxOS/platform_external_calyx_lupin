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

    private var onlineIndexLoaded: Boolean = false

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

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
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        val items = result.index.packages.mapNotNull { (packageName, packageV2) ->
            val signer = packageV2.getSigner()
            if (signer == null || signer.sha256.isEmpty()) {
                Log.w(TAG, "App had no signer: $packageName")
                return@mapNotNull null
            } else AppItem(
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
    private suspend fun onOnlineIndexLoaded(result: RepoResult) = withContext(Dispatchers.Main) {
        // If we are past selecting apps, don't update anymore, it is too late now
        val s = state.value as? UiState.SelectingApps ?: return@withContext
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        // Go through old items and update them if necessary, remembering package names we've seen
        val updatedItems = s.items.map { item ->
            // get packages and versions codes
            val packageV2 = result.index.packages[item.packageName] ?: return@map item
            // update current item, if new version code is higher than old one
            if (item.isValidUpdate(packageV2)) AppItem(
                item = item,
                result = result,
                packageV2 = packageV2,
                locales = locales,
            ) else item
        }.sortedBy { it.name }
        _state.value = UiState.SelectingApps(updatedItems, updatedItems.isNotEmpty())
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
