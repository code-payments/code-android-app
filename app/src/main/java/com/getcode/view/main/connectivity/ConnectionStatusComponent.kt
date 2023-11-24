package com.getcode.view.main.connectivity

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.getcode.theme.BrandLight
import com.getcode.theme.TopError

@Composable
fun ConnectionStatus(state: ConnectionState) {
    Row {
        when(state.connectionState) {
            ConnectionStatus.CONNECTING -> {
                CircularProgressIndicator(modifier = Modifier.height(10.dp))
                Text(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    text = "Loading",
                    color = BrandLight,
                    style = MaterialTheme.typography.body1.copy(
                    textAlign = TextAlign.Center))
            }

            ConnectionStatus.CONNECTED -> { }

            ConnectionStatus.DISCONNECTED -> Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = "No network connection",
                color = TopError,
                style = MaterialTheme.typography.body1.copy(
                textAlign = TextAlign.Center))
        }
    }
}


@Preview
@Composable
fun ConnectionReconnectingPreview(@PreviewParameter(ConnectionStatusProvider::class) state: ConnectionState) {
    ConnectionStatus(state)
}