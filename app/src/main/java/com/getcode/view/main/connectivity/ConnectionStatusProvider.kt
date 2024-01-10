package com.getcode.view.main.connectivity

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class ConnectionStatusProvider (
    override val values: Sequence<ConnectionState> = sequenceOf(
        ConnectionState(status = ConnectionStatus.CONNECTING),
        ConnectionState(status = ConnectionStatus.CONNECTED),
        ConnectionState(status = ConnectionStatus.DISCONNECTED)
    )
): PreviewParameterProvider<ConnectionState>