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

fun interface OnlineStateChangedListener {
    fun onOnlineStateChanged(online: Boolean)
}

class NetworkManager(
    context: Context,
    private val listener: OnlineStateChangedListener,
) : ConnectivityManager.NetworkCallback() {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _onlineState = MutableStateFlow(isOnlineNotMetered())
    val onlineState = _onlineState.asStateFlow()

    init {
        // monitor connectivity changes
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .addCapability(NET_CAPABILITY_NOT_METERED)
            .build()
        // this will call us right back with the current state
        connectivityManager.requestNetwork(networkRequest, this)
    }

    override fun onAvailable(network: Network) {
        val online = network.isOnline()
        Log.i(TAG, "onAvailable: $online")
        val wasSet = _onlineState.compareAndSet(!online, online)
        if (wasSet) listener.onOnlineStateChanged(online)
    }

    override fun onLost(network: Network) {
        val online = isOnlineNotMetered()
        Log.i(TAG, "onLost: $online")
        val wasSet = _onlineState.compareAndSet(!online, online)
        if (wasSet) listener.onOnlineStateChanged(online)
    }

    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        val online = caps.isOnline()
        Log.i(TAG, "onCapabilitiesChanged: $online")
        val wasSet = _onlineState.compareAndSet(!online, online)
        if (wasSet) listener.onOnlineStateChanged(online)
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
