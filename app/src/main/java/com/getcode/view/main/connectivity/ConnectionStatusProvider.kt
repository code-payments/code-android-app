package com.getcode.view.main.connectivity

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.getcode.utils.network.ConnectionType
import com.getcode.utils.network.NetworkState
import com.getcode.utils.network.SignalStrength

internal class NetworkStateProvider (
    override val values: Sequence<com.getcode.utils.network.NetworkState> = sequenceOf(
        com.getcode.utils.network.NetworkState(
            connected = false,
            type = com.getcode.utils.network.ConnectionType.Unknown,
            signalStrength = com.getcode.utils.network.SignalStrength.Unknown
        ),
        com.getcode.utils.network.NetworkState(
            connected = false,
            type = com.getcode.utils.network.ConnectionType.Wifi,
            signalStrength = com.getcode.utils.network.SignalStrength.Great
        ),
        com.getcode.utils.network.NetworkState(
            connected = true,
            type = com.getcode.utils.network.ConnectionType.Wifi,
            signalStrength = com.getcode.utils.network.SignalStrength.Great
        ),
    )
): PreviewParameterProvider<com.getcode.utils.network.NetworkState>