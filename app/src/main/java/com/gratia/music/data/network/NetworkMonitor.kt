package com.gratia.music.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether there is a network to talk to, as one source of truth for the app.
 *
 * The remote catalogue is the only part of Gratia that needs one, and when it
 * is missing the honest answer is to say so — not to spin, and not to render an
 * empty Home that reads as though the library had been wiped.
 *
 * [NET_CAPABILITY_INTERNET] is the signal rather than `VALIDATED`. Validated is
 * stricter and would catch a captive portal, but it also stays false for a while
 * on networks that are perfectly usable, and a false "you're offline" is a worse
 * failure than a request that goes out and fails — the request path already
 * handles failure, and the screens that consume this keep their cache either way.
 */
object NetworkMonitor {

    private const val TAG = "NetworkMonitor"

    private var connectivityManager: ConnectivityManager? = null

    /**
     * Optimistic until told otherwise: a process that has not been given a
     * Context yet must not report the app offline and suppress its first fetch.
     */
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Call once, from `Application.onCreate`. Safe to call more than once. */
    fun initialize(context: Context) {
        if (connectivityManager != null) return
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        connectivityManager = manager
        _isOnline.value = currentlyOnline(manager)

        try {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = currentlyOnline(manager)
                }

                override fun onLost(network: Network) {
                    // The default network going away is not the same as being
                    // offline — Wi-Fi dropping hands over to cellular — so the
                    // answer is re-read rather than assumed.
                    _isOnline.value = currentlyOnline(manager)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    _isOnline.value =
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        } catch (e: Exception) {
            // A missing ACCESS_NETWORK_STATE permission or a vendor quirk must
            // not take the app down; the optimistic default stands.
            Log.w(TAG, "could not register network callback: ${e.message}")
        }
    }

    /**
     * Synchronous read, for code deciding whether to even attempt a request.
     * Reports online when uninitialized, so nothing is suppressed by accident.
     */
    fun isCurrentlyOnline(): Boolean =
        connectivityManager?.let(::currentlyOnline) ?: true

    /**
     * Whether the connection in hand is metered — the per-network quality
     * settings read this. Optimistic (false) when uninitialized, so nothing is
     * capped by accident.
     */
    fun isMetered(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun currentlyOnline(manager: ConnectivityManager): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
