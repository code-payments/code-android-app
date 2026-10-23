package com.getcode.utils.network.connectivity

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.getcode.utils.network.ConnectionType
import com.getcode.utils.network.NetworkState
import com.getcode.utils.network.SignalStrength

class NetworkStateProvider (
    override val values: Sequence<NetworkState> = sequenceOf(
        NetworkState(
            connected = false,
            type = ConnectionType.Unknown,
            signalStrength = SignalStrength.Unknown
        ),
        NetworkState(
            connected = false,
            type = ConnectionType.Wifi,
            signalStrength = SignalStrength.Great
        ),
        NetworkState(
            connected = true,
            type = ConnectionType.Wifi,
            signalStrength = SignalStrength.Great
        ),
    )
): PreviewParameterProvider<NetworkState>