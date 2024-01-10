package com.getcode.view.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.manager.BottomBarManager
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.util.rememberedClickable

@Composable
fun BottomBarView(
    bottomBarMessage: BottomBarManager.BottomBarMessage?,
    onClose: (bottomBarActionType: BottomBarManager.BottomBarActionType?) -> Unit,
    onBackPressed: () -> Unit
) {
    bottomBarMessage ?: return

    BackHandler {
        onBackPressed()
    }

    Box(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .background(
                    when (bottomBarMessage.type) {
                        BottomBarManager.BottomBarMessageType.DEFAULT -> CodeTheme.colors.error
                        BottomBarManager.BottomBarMessageType.REMOTE_SEND -> BrandLight
                    }
                )
                .padding(CodeTheme.dimens.inset),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            CompositionLocalProvider(LocalContentColor provides White) {
                Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                    Text(
                        style = CodeTheme.typography.subtitle1,
                        text = bottomBarMessage.title
                    )
                    Text(
                        style = CodeTheme.typography.body2,
                        text = bottomBarMessage.subtitle,
                        color = LocalContentColor.current.copy(alpha = 0.8f)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                CodeButton(
                    onClick = {
                        bottomBarMessage.onPositive()
                        onClose(BottomBarManager.BottomBarActionType.Positive)
                    },
                    textColor =
                    when (bottomBarMessage.type) {
                        BottomBarManager.BottomBarMessageType.DEFAULT -> CodeTheme.colors.error
                        BottomBarManager.BottomBarMessageType.REMOTE_SEND -> BrandLight
                    },
                    buttonState = ButtonState.Filled,
                    text = bottomBarMessage.positiveText
                )
                CodeButton(
                    onClick = {
                        bottomBarMessage.onNegative()
                        onClose(BottomBarManager.BottomBarActionType.Negative)
                    },
                    textColor = White,
                    buttonState = ButtonState.Filled10,
                    text = bottomBarMessage.negativeText
                )
                bottomBarMessage.tertiaryText?.let {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rememberedClickable {
                                bottomBarMessage.onTertiary()
                                onClose(BottomBarManager.BottomBarActionType.Tertiary)
                            }
                            .padding(vertical = CodeTheme.dimens.grid.x2),
                        style = CodeTheme.typography.button,
                        text = it
                    )
                }
            }
        }
    }
}