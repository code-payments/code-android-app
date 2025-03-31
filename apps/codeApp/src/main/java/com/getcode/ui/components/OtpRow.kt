package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview


@Preview
@Composable
fun OtpRow(
    modifier: Modifier = Modifier,
    length: Int = 4,
    values: CharArray = charArrayOf(),
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until length) {
            val text = if (i < values.size) values[i] else ' '
            val isHighlighted = values.size == i
            OtpBox(
                character = text.toString(),
                onClick = onClick,
                isHighlighted = isHighlighted
            )
        }
    }
}