package com.getcode.ui.components.bars

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.util.fastForEach
import com.getcode.manager.BottomBarManager
import com.getcode.theme.Black40
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.core.rememberedClickable
import com.getcode.util.resources.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BottomBarContainer(barMessages: BarMessages) {
    val scope = rememberCoroutineScope()
    val bottomBarMessage by barMessages.bottomBar.collectAsState()
    val bottomBarVisibleState = remember(bottomBarMessage?.id) { MutableTransitionState(false) }
    var bottomBarMessageDismissId by remember { mutableLongStateOf(0L) }
    val onClose: suspend (fromAction: Boolean) -> Unit = { fromAction ->
        bottomBarMessageDismissId = bottomBarMessage?.id ?: 0
        bottomBarVisibleState.targetState = false
        bottomBarMessage?.onClose?.invoke(fromAction)

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
            onClose(false)
        }
    }

    val scrimAlpha by animateFloatAsState(if (bottomBarMessage?.showScrim == true) 1f else 0f, label = "scrim visibility")

    if (bottomBarVisibleState.targetState && bottomBarMessage != null) {
        bottomBarMessage?.let {
            if (it.showScrim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Black40)
                        .rememberedClickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (it.isDismissible) {
                                scope.launch { onClose(false) }
                            }
                        }
                )
            } else {
                if (it.isDismissible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rememberedClickable(indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                scope.launch { onClose(false) }
                            }
                    )
                }
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
        val closeWith: (fromAction: Boolean) -> Unit = { fromAction ->
            scope.launch { onClose(fromAction) }
        }
        BottomBarView(bottomBarMessage = bottomBarMessage, closeWith, onBackPressed = { closeWith(false)})
    }
}

@Composable
fun BottomBarView(
    bottomBarMessage: BottomBarManager.BottomBarMessage?,
    onClose: (fromAction: Boolean) -> Unit,
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
                        BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> CodeTheme.colors.error
                        BottomBarManager.BottomBarMessageType.REMOTE_SEND -> CodeTheme.colors.brandLight
                        BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.brand
                    }
                )
                .padding(top = CodeTheme.dimens.inset)
                .padding(horizontal = CodeTheme.dimens.inset)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            if (bottomBarMessage.title.isNotEmpty()) {
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
            }
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                bottomBarMessage.actions.fastForEach { action ->
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            action.onClick()
                            onClose(true)
                        },
                        textColor = when (bottomBarMessage.type) {
                            BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> CodeTheme.colors.error
                            BottomBarManager.BottomBarMessageType.REMOTE_SEND -> CodeTheme.colors.brandLight
                            BottomBarManager.BottomBarMessageType.THEMED -> Brand
                        },
                        buttonState = when (action.style) {
                            BottomBarManager.BottomBarButtonStyle.Filled -> ButtonState.Filled
                            BottomBarManager.BottomBarButtonStyle.Filled10 -> ButtonState.Filled10
                        },
                        text = action.text
                    )
                }
                if (bottomBarMessage.showCancel) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rememberedClickable {
                                onClose(false)
                            }
                            .padding(vertical = CodeTheme.dimens.grid.x3),
                        style = CodeTheme.typography.textMedium,
                        textAlign = TextAlign.Center,
                        color = when (bottomBarMessage.type) {
                            BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                            BottomBarManager.BottomBarMessageType.REMOTE_SEND -> White

                            BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.textSecondary
                        },
                        text = stringResource(R.string.action_cancel)
                    )
                } else {
                    Spacer(Modifier.height(CodeTheme.dimens.inset))
                }
            }
        }
    }
}