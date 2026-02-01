package com.flipcash.app.phone.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.theme.CodeTheme
import com.getcode.theme.WindowSizeClass
import com.getcode.ui.components.TextInput
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun OtpInputField(
    state: TextFieldState,
    lengthNeeded: Int,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onLengthReached: (() -> Unit)? = null,
) {
    val keyboardController = rememberKeyboardController()

    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .map { it.length }
            .filter { it == lengthNeeded }
            .onEach {
                onLengthReached?.invoke()
            }.launchIn(this)
    }

    Box(modifier = modifier) {
        TextInput(
            modifier = Modifier
                .alpha(0f)
                .focusRequester(focusRequester),
            inputTransformation = object : InputTransformation {
                override fun TextFieldBuffer.transformInput() {
                    if (this.length > lengthNeeded) {
                        replace(0, lengthNeeded - 1, this.originalText.substring(0, lengthNeeded - 1))
                    }
                }
            },
            state = state,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        )

        OtpRow(
            modifier = Modifier
                .padding(top = CodeTheme.dimens.grid.x1),
            length = lengthNeeded,
            values = state.text.toString().toCharArray(),
            onClick = {
                focusRequester.requestFocus()
                keyboardController.show()
            }
        )
    }
}

@Composable
private fun OtpRow(
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

@Composable
private fun OtpBox(
    character: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {

    val height = when (CodeTheme.dimens.heightWindowSizeClass) {
        WindowSizeClass.COMPACT -> CodeTheme.dimens.grid.x9
        else -> CodeTheme.dimens.grid.x11
    }

    Box(
        modifier = modifier
            .padding(CodeTheme.dimens.grid.x1)
            .height(height)
            .width(CodeTheme.dimens.grid.x7)
            .clip(CodeTheme.shapes.small)
            .rememberedClickable(onClick = onClick)
            .border(
                border = if (isHighlighted)
                    BorderStroke(CodeTheme.dimens.thickBorder, color = Color.White.copy(0.34f))
                else
                    BorderStroke(CodeTheme.dimens.border, color = CodeTheme.colors.border),
                shape = CodeTheme.shapes.small
            )
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Text(
            text = character,
            modifier = Modifier
                .align(Alignment.Center),
            style = CodeTheme.typography.displayExtraSmall,
            color = Color.White,
        )
    }
}

@Preview
@Composable
private fun Preview_OptInput() {
    FlipcashPreview(showBackground = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
        ) {
            OtpInputField(
                state = rememberTextFieldState(),
                lengthNeeded = 4,
            )

            OtpInputField(
                state = rememberTextFieldState(initialText = "123"),
                lengthNeeded = 6,
            )
        }
    }
}