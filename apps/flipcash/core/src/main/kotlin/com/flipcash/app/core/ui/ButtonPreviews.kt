package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.onramp.ui.buildExternalWalletButtonLabel
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Preview
@Composable
private fun CodeButtonPreviews() {
    FlipcashPreview(showBackground = true) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val states = ButtonState.entries
            for (state in states) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CodeButton(
                        modifier = Modifier.weight(1f),
                        onClick = {},
                        text = state.name,
                        buttonState = state,
                    )
                    CodeButton(
                        modifier = Modifier.weight(1f),
                        onClick = {},
                        text = state.name,
                        buttonState = state,
                        enabled = false,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CodeButton(
                    modifier = Modifier.weight(1f),
                    onClick = {},
                    text = "Loading",
                    buttonState = ButtonState.Filled,
                    enabled = false,
                    isLoading = true,
                )
                CodeButton(
                    modifier = Modifier.weight(1f),
                    onClick = {},
                    text = "Success",
                    enabled = false,
                    buttonState = ButtonState.Filled,
                    isSuccess = true,
                )
            }

            with (buildExternalWalletButtonLabel("Confirm in", provider = OnRampProvider.Phantom, Color.Black)) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {},
                    text = first,
                    inlineContent = second,
                    buttonState = ButtonState.Filled,
                )
            }

            with (buildNotifyButtonLabel()) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {},
                    enabled = false,
                    text = first,
                    inlineContent = second,
                    buttonState = ButtonState.Filled,
                )
            }
        }
    }
}