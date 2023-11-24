package com.getcode.view.main.connectivity

data class ConnectionState(
    val connectionState: ConnectionStatus = ConnectionStatus.CONNECTED
)
enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}