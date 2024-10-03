package com.getcode.ui.components.bars

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.getcode.manager.BottomBarManager
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.rememberedClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BottomBarContainer(barMessages: BarMessages) {
    val scope = rememberCoroutineScope()
    val bottomBarMessage by barMessages.bottomBar.collectAsState()
    val bottomBarVisibleState = remember(bottomBarMessage?.id) { MutableTransitionState(false) }
    var bottomBarMessageDismissId by remember { mutableLongStateOf(0L) }
    val onClose: suspend (bottomBarActionType: BottomBarManager.BottomBarActionType?) -> Unit = {
        bottomBarMessageDismissId = bottomBarMessage?.id ?: 0
        bottomBarVisibleState.targetState = false
        bottomBarMessage?.onClose?.invoke(it)

        delay(100)
        BottomBarManager.setMessageShown(bottomBarMessageDismissId)
    }

    // handle changes in visible state
    LaunchedEffect(bottomBarVisibleState) {
        if (!bottomBarVisibleState.targetState && !bottomBarVisibleState.currentState) {
            delay(50)
            bottomBarVisibleState.targetState = bottomBarMessage != null

            if (bottomBarMessageDismissId == bottomBarMessage?.id) {
                bottomBarMessageDismissId = 0
            }
        }
    }

    // handle provided timeout duration; triggering onClose with no action
    LaunchedEffect(bottomBarMessage) {
        bottomBarMessage?.timeoutSeconds?.let {
            delay(it * 1000L)
            onClose(null)
        }
    }

    // add transparent touch handler if dismissible
    if (bottomBarVisibleState.targetState && bottomBarMessage != null) {
        bottomBarMessage?.let {
            if (it.isDismissible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rememberedClickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            scope.launch { onClose(null) }
                        }
                )
            }
        }

    }

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visibleState = bottomBarVisibleState,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ),
    ) {
        val closeWith: (BottomBarManager.BottomBarActionType?) -> Unit = { type ->
            scope.launch { onClose(type) }
        }
        BottomBarView(bottomBarMessage = bottomBarMessage, closeWith, onBackPressed = { closeWith(null)})
    }
}

@Composable
fun BottomBarView(
    bottomBarMessage: BottomBarManager.BottomBarMessage?,
    onClose: (bottomBarActionType: BottomBarManager.BottomBarActionType?) -> Unit,
    onBackPressed: () -> Unit
) {
    bottomBarMessage ?: return

    BackHandler(enabled = bottomBarMessage.isDismissible) {
        onBackPressed()
    }

    Box(
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
                .padding(CodeTheme.dimens.inset)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            CompositionLocalProvider(LocalContentColor provides White) {
                Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                    Text(
                        style = CodeTheme.typography.textLarge,
                        text = bottomBarMessage.title
                    )
                    Text(
                        style = CodeTheme.typography.textSmall,
                        text = bottomBarMessage.subtitle,
                        color = LocalContentColor.current.copy(alpha = 0.8f)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
                        style = CodeTheme.typography.textMedium,
                        textAlign = TextAlign.Center,
                        color = White,
                        text = it
                    )
                }
            }
        }
    }
}