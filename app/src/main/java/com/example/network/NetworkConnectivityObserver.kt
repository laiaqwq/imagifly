package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class TransportType(val label: String) {
    WIFI("Wi-Fi 网络"),
    CELLULAR("移动数据网络"),
    ETHERNET("有线以太网"),
    VPN("VPN 专网"),
    OTHER("未知网络"),
    NONE("无网络连接")
}

data class NetworkStatusSnapshot(
    val isConnected: Boolean,
    val isValidated: Boolean,
    val transportType: TransportType
) {
    val isOnline: Boolean
        get() = isConnected && isValidated
}

class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getCurrentStatus(): NetworkStatusSnapshot {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatusSnapshot(
            isConnected = false,
            isValidated = false,
            transportType = TransportType.NONE
        )
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkStatusSnapshot(
                isConnected = false,
                isValidated = false,
                transportType = TransportType.NONE
            )
        return parseCapabilities(caps)
    }

    fun observe(): Flow<NetworkStatusSnapshot> = callbackFlow {
        trySend(getCurrentStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(parseCapabilities(networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(getCurrentStatus())
            }

            override fun onUnavailable() {
                trySend(
                    NetworkStatusSnapshot(
                        isConnected = false,
                        isValidated = false,
                        transportType = TransportType.NONE
                    )
                )
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: SecurityException) {
            // Fallback if permission check fails in test environment
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    private fun parseCapabilities(caps: NetworkCapabilities): NetworkStatusSnapshot {
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> TransportType.VPN
            hasInternet -> TransportType.OTHER
            else -> TransportType.NONE
        }
        return NetworkStatusSnapshot(
            isConnected = hasInternet,
            isValidated = isValidated || hasInternet,
            transportType = transport
        )
    }
}
