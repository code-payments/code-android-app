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
import com.getcode.theme.CodeTheme
import com.getcode.utils.network.ConnectionType
import com.getcode.utils.network.NetworkState
import com.getcode.ui.components.CodeCircularProgressIndicator

@Composable
fun ConnectionStatus(state: NetworkState) {
    Row {
        when  {
            !state.connected && state.type != ConnectionType.Unknown -> {
                CodeCircularProgressIndicator(
                    modifier = Modifier.height(
                        CodeTheme.dimens.grid.x2
                    )
                )
                Text(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    text = "Loading",
                    color = CodeTheme.colors.textSecondary,
                    style = CodeTheme.typography.textMedium.copy(
                    textAlign = TextAlign.Center))
            }

            !state.connected -> Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = "No network connection",
                color = CodeTheme.colors.errorText,
                style = CodeTheme.typography.textMedium.copy(
                textAlign = TextAlign.Center))
        }
    }
}


@Preview
@Composable
fun ConnectionReconnectingPreview(@PreviewParameter(NetworkStateProvider::class) state: NetworkState) {
    ConnectionStatus(state)
}