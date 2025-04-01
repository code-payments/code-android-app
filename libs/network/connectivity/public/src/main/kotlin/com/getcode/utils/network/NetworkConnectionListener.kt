package com.getcode.utils.network

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val LocalNetworkObserver: ProvidableCompositionLocal<NetworkConnectivityListener> = staticCompositionLocalOf { NetworkObserverStub() }

interface NetworkConnectivityListener {
    val state: StateFlow<NetworkState>
    val isConnected: Boolean
    val type: ConnectionType
}

enum class ConnectionType {
    Unknown,
    Cellular,
    Wifi;

    fun isWifi() = this == Wifi
    fun isCellular() = this == Cellular
    fun isValid() = isWifi() || isCellular()
}

enum class SignalStrength {
    Unknown,
    Weak,
    Poor,
    Good,
    Great,
    Strong;

    fun isWeakOrPoor() = this == Weak || this == Poor
    fun isKnown() = this != Unknown
}

data class NetworkState(
    val connected: Boolean,
    val signalStrength: SignalStrength,
    val type: ConnectionType
) {
    companion object {
        val Default = NetworkState(false, SignalStrength.Unknown, ConnectionType.Unknown)
    }
}

class NetworkObserverStub : NetworkConnectivityListener {
    override val isConnected: Boolean = false
    override val type: ConnectionType = ConnectionType.Unknown
    override val state: StateFlow<NetworkState> = MutableStateFlow(NetworkState.Default).asStateFlow()
}