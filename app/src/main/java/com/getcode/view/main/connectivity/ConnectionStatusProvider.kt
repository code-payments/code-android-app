package com.getcode.view.main.connectivity

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class ConnectionStatusProvider (
    override val values: Sequence<ConnectionState> = sequenceOf(
        ConnectionState(connectionState = ConnectionStatus.CONNECTING),
        ConnectionState(connectionState = ConnectionStatus.CONNECTED),
        ConnectionState(connectionState = ConnectionStatus.DISCONNECTED)
    )
): PreviewParameterProvider<ConnectionState>