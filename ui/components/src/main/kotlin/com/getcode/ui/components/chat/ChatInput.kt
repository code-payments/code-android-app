package com.getcode.ui.components.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.extraSmall
import com.getcode.theme.inputColors
import com.getcode.ui.components.R
import com.getcode.ui.components.TextInput

@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String = "",
    state: TextFieldState = rememberTextFieldState(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    onSendMessage: () -> Unit,
) {
    val shape = CodeTheme.shapes.medium
    val sendVisible = state.text.isNotEmpty()
    val sendSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 4000f)
    val sendAlpha by animateFloatAsState(
        targetValue = if (sendVisible) 1f else 0f,
        animationSpec = sendSpec,
        label = "send button alpha",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (sendVisible) 1f else 0.6f,
        animationSpec = sendSpec,
        label = "send button scale",
    )
    Row(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clip(shape)
            .then(modifier)
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.Bottom
    ) {
        TextInput(
            modifier = Modifier
                .weight(1f)
                .background(Color.Transparent, shape = CodeTheme.shapes.medium)
                .focusRequester(focusRequester),
            minHeight = 40.dp,
            enabled = enabled,
            state = state,
            placeholder = hint,
            shape = CodeTheme.shapes.medium,
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
            contentAlignment = Alignment.Bottom,
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.staticGrid.x3,
                top = CodeTheme.dimens.staticGrid.x2,
                end = CodeTheme.dimens.staticGrid.x3,
                bottom = CodeTheme.dimens.staticGrid.x2,
            ),
            colors = inputColors(
                backgroundColor = Color(0xAD1E1E1E),
                borderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
            trailingIcon = {
                Icon(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = sendAlpha
                            scaleX = sendScale
                            scaleY = sendScale
                        }
                        .padding(vertical = CodeTheme.dimens.grid.x1)
                        .padding(end = CodeTheme.dimens.staticGrid.x1)
                        .background(
                            Color.White,
                            shape = CodeTheme.shapes.extraSmall
                        )
                        .clip(CodeTheme.shapes.extraSmall)
                        .clickable(enabled = sendVisible) { onSendMessage() }
                        .padding(CodeTheme.dimens.staticGrid.x1)
                        .size(CodeTheme.dimens.staticGrid.x5),
                    painter = painterResource(R.drawable.ic_arrow_up),
                    tint = Color.Black,
                    contentDescription = "Send message"
                )
            }
        )
    }
}

@Preview
@Composable
private fun Preview_ChatInput_Empty() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                onSendMessage = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_ChatInput_Typing() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                onSendMessage = {},
                state = TextFieldState("That’s very kind of you. I ha")
            )
        }
    }
}

private val ChatInputButtonSize
    @Composable get() = CodeTheme.dimens.staticGrid.x7