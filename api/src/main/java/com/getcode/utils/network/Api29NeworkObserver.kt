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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

@RequiresApi(Build.VERSION_CODES.Q)
class Api29NetworkObserver(
    private val connectivityManager: ConnectivityManager,
    private val telephonyManager: TelephonyManager,
) : NetworkConnectivityListener {

    @OptIn(FlowPreview::class)
    private val connected: Flow<Boolean> = callbackFlow {
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onUnavailable() {
                trySend(false)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(listener)
        awaitClose { connectivityManager.unregisterNetworkCallback(listener) }
    }
        .onStart { emit(connectivityManager.activeNetwork != null) }
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
            override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) {
                trySend(signalStrength.level.toSignalStrength())
            }
        }
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
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
                    WifiManager.calculateSignalLevel(networkCapabilities.signalStrength, 5)
                trySend(level.toSignalStrength())
            }

            override fun onLost(network: Network) {
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
        val type = connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && wifi != SignalStrength.Unknown) {
                    ConnectionType.Wifi
                } else {
                    ConnectionType.Cellular
                }
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
            SharingStarted.Eagerly,
            NetworkState(true, SignalStrength.Unknown, ConnectionType.Unknown)
        ) // make state always available

    override val isConnected: Boolean
        get() = state.value.connected

    override val type: ConnectionType
        get() = state.value.type
}