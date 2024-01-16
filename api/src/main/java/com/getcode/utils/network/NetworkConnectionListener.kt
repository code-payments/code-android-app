package com.getcode.utils.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

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
}

enum class SignalStrength {
    Unknown,
    Weak,
    Poor,
    Good,
    Great,
    Strong;

    fun isWeakOrPoor() = this == Weak || this == Poor
}

data class NetworkState(
    val connected: Boolean,
    val signalStrength: SignalStrength,
    val type: ConnectionType
)

class NetworkObserverStub : NetworkConnectivityListener {
    override val isConnected: Boolean = false
    override val type: ConnectionType = ConnectionType.Unknown
    override val state: StateFlow<NetworkState> = MutableStateFlow(
        NetworkState(false, SignalStrength.Unknown, ConnectionType.Unknown)
    ).asStateFlow()
}



internal fun Int.toSignalStrength() = when (this) {
    0 -> SignalStrength.Weak
    1 -> SignalStrength.Poor
    2 -> SignalStrength.Good
    3 -> SignalStrength.Great
    4 -> SignalStrength.Strong
    else -> SignalStrength.Unknown
}