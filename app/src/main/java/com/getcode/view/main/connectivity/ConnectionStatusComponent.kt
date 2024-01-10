package com.getcode.view.main.connectivity

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.view.components.CodeCircularProgressIndicator

@Composable
fun ConnectionStatus(state: ConnectionState) {
    Row {
        when(state.status) {
            ConnectionStatus.CONNECTING -> {
                CodeCircularProgressIndicator(modifier = Modifier.height(CodeTheme.dimens.grid.x2))
                Text(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    text = "Loading",
                    color = BrandLight,
                    style = CodeTheme.typography.body1.copy(
                    textAlign = TextAlign.Center))
            }

            ConnectionStatus.CONNECTED -> { }

            ConnectionStatus.DISCONNECTED -> Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = "No network connection",
                color = CodeTheme.colors.errorText,
                style = CodeTheme.typography.body1.copy(
                textAlign = TextAlign.Center))
        }
    }
}


@Preview
@Composable
fun ConnectionReconnectingPreview(@PreviewParameter(ConnectionStatusProvider::class) state: ConnectionState) {
    ConnectionStatus(state)
}