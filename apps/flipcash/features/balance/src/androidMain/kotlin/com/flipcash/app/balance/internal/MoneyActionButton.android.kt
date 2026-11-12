package com.flipcash.app.balance.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal actual fun MoneyActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) = CodeButton(
    text = text,
    modifier = modifier,
    buttonState = if (primary) ButtonState.Filled else ButtonState.Filled10,
    onClick = onClick,
)
