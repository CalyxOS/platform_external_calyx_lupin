package org.calyxos.lupin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NetworkManager"

class NetworkManager(context: Context) : ConnectivityManager.NetworkCallback() {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _onlineState = MutableStateFlow(isOnlineNotMetered())
    val onlineState = _onlineState.asStateFlow()

    init {
        // monitor connectivity changes
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .addCapability(NET_CAPABILITY_NOT_METERED)
            .build()
        connectivityManager.requestNetwork(networkRequest, this)
    }

    override fun onAvailable(network: Network) {
        _onlineState.value = network.isOnline().also { Log.i(TAG, "onAvailable: $it") }
    }

    override fun onLost(network: Network) {
        _onlineState.value = isOnlineNotMetered().also { Log.i(TAG, "onLost: $it") }
    }

    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        _onlineState.value = caps.isOnline().also { Log.i(TAG, "onCapabilitiesChanged: $it") }
    }

    private fun isOnlineNotMetered(): Boolean {
        if (BuildConfig.DEBUG) return false
        val currentNetwork = connectivityManager.activeNetwork
        return currentNetwork?.isOnline() ?: false
    }

    private fun Network.isOnline(): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(this)
        return caps?.isOnline() ?: false
    }

    private fun NetworkCapabilities.isOnline(): Boolean {
        return hasCapability(NET_CAPABILITY_INTERNET) && hasCapability(NET_CAPABILITY_NOT_METERED)
    }
}
