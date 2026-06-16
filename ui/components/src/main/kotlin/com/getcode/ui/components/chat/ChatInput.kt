package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.Composable
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
import com.getcode.theme.extraLarge
import com.getcode.theme.inputColors
import com.getcode.ui.components.R
import com.getcode.ui.components.TextInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.ui.Alignment
import com.getcode.theme.extraSmall

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
    Row(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clip(shape)
            .then(modifier)
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
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
                AnimatedContent(
                    targetState = state.text.isNotEmpty(),
                    label = "show/hide send button",
                    transitionSpec = {
                        fadeIn(spring()) togetherWith fadeOut(spring())
                    }
                ) { show ->
                    if (show) {
                        Icon(
                            modifier = Modifier
                                .padding(vertical = CodeTheme.dimens.grid.x1)
                                .padding(end = CodeTheme.dimens.staticGrid.x2)
                                .background(
                                    Color.White,
                                    shape = CodeTheme.shapes.extraSmall
                                ).clip(CodeTheme.shapes.extraSmall)
                                .clickable { onSendMessage() }
                                .padding(CodeTheme.dimens.staticGrid.x1)
                                .size(CodeTheme.dimens.staticGrid.x5),
                            imageVector = Icons.Rounded.ArrowUpward,
                            tint = Color.Black,
                            contentDescription = "Send message"
                        )
                    } else {
                        Spacer(Modifier.requiredSize(CodeTheme.dimens.staticGrid.x9))
                    }
                }
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