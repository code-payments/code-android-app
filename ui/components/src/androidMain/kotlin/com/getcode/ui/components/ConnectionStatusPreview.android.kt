package com.getcode.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.getcode.utils.network.NetworkState
import com.getcode.utils.network.connectivity.NetworkStateProvider

@Preview
@Composable
fun ConnectionReconnectingPreview(@PreviewParameter(NetworkStateProvider::class) state: NetworkState) {
    ConnectionStatus(state)
}
