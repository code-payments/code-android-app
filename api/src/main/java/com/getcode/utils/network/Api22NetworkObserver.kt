package com.getcode.utils.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

class Api22NetworkObserver(
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager,
    private val telephonyManager: TelephonyManager,
): NetworkConnectivityListener {
    @OptIn(FlowPreview::class)
    private val connected: Flow<Boolean> = callbackFlow {
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }


        val builder = NetworkRequest.Builder()
        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        connectivityManager.registerNetworkCallback(builder.build(), listener)
        awaitClose { connectivityManager.unregisterNetworkCallback(listener) }
    }
        .onStart { emit(connectivityManager.activeNetworkInfo != null)  }
        .debounce(2000) // bridge wifi <> mobile switch
        .distinctUntilChanged()
        .shareIn(
            CoroutineScope(Dispatchers.Main),
            SharingStarted.WhileSubscribed(replayExpirationMillis = 1000), // don't replay old values forever
            1,
        )

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // TelephonyCallback is API 31+
    private val mobileSignalStrength: Flow<SignalStrength> = callbackFlow {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthChanged(asu: Int) {
                super.onSignalStrengthChanged(asu)
                val dBm = asu.asuToDbm()
                val rssi = dBm.dbmToRssi()
                val level = rssi.toSignalStrength()
                trySend(level)
            }
        }
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTH)
        awaitClose { telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }.onStart { emit(SignalStrength.Unknown) }
        .distinctUntilChanged()
        .shareIn(
            CoroutineScope(Dispatchers.Main),
            SharingStarted.WhileSubscribed(replayExpirationMillis = 1000), // don't replay old values forever
            1,
        )

    @Suppress("DEPRECATION")
    private val wifiSignalStrength: Flow<SignalStrength> = callbackFlow {
        val connectivityListener = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val level =
                    WifiManager.calculateSignalLevel(wifiManager.connectionInfo.rssi, 5)
                trySend(level.toSignalStrength())
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                trySend((-1).toSignalStrength())
            }

            override fun onUnavailable() {
                trySend((-1).toSignalStrength())
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            connectivityListener
        )
        awaitClose { connectivityManager.unregisterNetworkCallback(connectivityListener) }
    }.onStart { emit(SignalStrength.Unknown) }
        .distinctUntilChanged()
        .shareIn(
            CoroutineScope(Dispatchers.Main),
            SharingStarted.WhileSubscribed(replayExpirationMillis = 1000), // don't replay old values forever
            1,
        )

    override val state: StateFlow<NetworkState> = combine(
        connected,
        mobileSignalStrength,
        wifiSignalStrength,
    ) { connected, mobile, wifi ->
        val type = connectivityManager.activeNetworkInfo?.let { networkInfo ->
            if (networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                ConnectionType.Wifi
            } else {
                ConnectionType.Cellular
            }
        } ?: ConnectionType.Unknown
        NetworkState(
            connected = connected,
            signalStrength = maxOf(mobile, wifi),
            type = type,
        )
    }
        .stateIn(
            CoroutineScope(Dispatchers.Main),
            SharingStarted.WhileSubscribed(),
            NetworkState(connectivityManager.activeNetworkInfo != null, SignalStrength.Unknown, ConnectionType.Unknown)
        ) // make state always available

    override val isConnected: Boolean
        get() = state.value.connected

    override val type: ConnectionType
        get() = state.value.type
}

private fun Int.asuToDbm(): Int {
    return (2 * this) - 113
}

private fun Int.dbmToRssi(): Int {
    // rssi - 95 = dBm
    // dBm + 95 = rssi
    // https://documentation.meraki.com/MR/Monitoring_and_Reporting/Location_Analytics
    return this + 95
}