package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.utils.withTopBorder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    sendCashEnabled: Boolean = false,
    onSendMessage: () -> Unit,
    onSendCash: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .withTopBorder()
            .padding(CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.Bottom
    ) {
        if (sendCashEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .border(width = 1.dp, color = Color.White, shape = CircleShape)
                    .clip(CircleShape)
                    .rememberedClickable { onSendCash() }
                    .size(ChatInput_Size)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier
                        .size(CodeTheme.dimens.staticGrid.x6),
                    painter = painterResource(id = R.drawable.ic_kin_white),
                    tint = Color.White,
                    contentDescription = "Send message"
                )
            }
        }

        TextInput(
            modifier = Modifier
                .weight(1f),
            minHeight = 40.dp,
            state = state,
            shape = CodeTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
            contentPadding = PaddingValues(
                start = 8.dp + CodeTheme.dimens.staticGrid.x2,
                top = 8.dp,
                end = 8.dp + CodeTheme.dimens.staticGrid.x2,
                bottom = 8.dp
            ),
            colors = inputColors(
                backgroundColor = Color.White,
                textColor = CodeTheme.colors.background,
                cursorColor = CodeTheme.colors.brand,
            )
        )
        AnimatedContent(
            targetState = state.text.isNotEmpty(),
            label = "show/hide send button",
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { it }
            }
        ) { show ->
            if (show) {
                Box(
                    modifier = Modifier
                        .size(ChatInput_Size)
                        .align(Alignment.Bottom)
                        .background(CodeTheme.colors.tertiary, shape = CircleShape)
                        .clip(CircleShape)
                        .rememberedClickable { onSendMessage() }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier
                            .size(CodeTheme.dimens.staticGrid.x6),
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        tint = Color.White,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun Preview_ChatInput() {
    DesignSystem {
        ChatInput(
            sendCashEnabled = true,
            onSendMessage = {},
            onSendCash = {}
        )
    }
}

private val ChatInput_Size
    @Composable get() = CodeTheme.dimens.grid.x8