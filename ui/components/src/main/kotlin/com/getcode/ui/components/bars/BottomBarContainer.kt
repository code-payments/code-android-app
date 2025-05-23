package com.getcode.ui.components.bars

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SelectedBottomBarAction
import com.getcode.theme.Black40
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.scaled
import com.getcode.util.resources.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BottomBarContainer(barMessages: BarMessages) {
    val scope = rememberCoroutineScope()
    val bottomBarMessage by barMessages.bottomBar.collectAsState()
    val bottomBarVisibleState = remember(bottomBarMessage?.id) { MutableTransitionState(false) }
    var bottomBarMessageDismissId by remember { mutableLongStateOf(0L) }
    val animationScale by rememberAnimationScale()
    val onClose: suspend (selection: SelectedBottomBarAction) -> Unit = { selection ->
        bottomBarMessageDismissId = bottomBarMessage?.id ?: 0
        bottomBarVisibleState.targetState = false

        delay(300.scaled(animationScale))
        BottomBarManager.setMessageShown(bottomBarMessageDismissId)
        bottomBarMessage?.onClose?.invoke(selection)
    }

    // handle changes in visible state
    LaunchedEffect(bottomBarVisibleState) {
        if (!bottomBarVisibleState.targetState && !bottomBarVisibleState.currentState) {
            delay(50.scaled(animationScale))
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
            onClose(SelectedBottomBarAction(-1))
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
                                scope.launch { onClose(SelectedBottomBarAction(-1)) }
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
                                scope.launch { onClose(SelectedBottomBarAction(-1)) }
                            }
                    )
                }
            }
        }
    }

    AnimatedContent(
        modifier = Modifier.fillMaxSize()
            .clipToBounds(),
        targetState = bottomBarVisibleState.targetState,
        transitionSpec = {
            slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) togetherWith slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        },
        label = "BottomBarAnimation"
    ) { isVisible ->
        if (isVisible) {
            val closeWith: (selection: SelectedBottomBarAction) -> Unit = { selection ->
                scope.launch { onClose(selection) }
            }
            BottomBarView(
                bottomBarMessage = bottomBarMessage,
                onClose = closeWith,
                onBackPressed = { closeWith(SelectedBottomBarAction(-1)) }
            )
        }
    }
}

@Composable
fun BottomBarView(
    bottomBarMessage: BottomBarManager.BottomBarMessage?,
    onClose: (selection: SelectedBottomBarAction) -> Unit,
    onBackPressed: () -> Unit
) {
    bottomBarMessage ?: return

    BackHandler(enabled = bottomBarMessage.isDismissible) {
        onBackPressed()
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .background(bottomBarMessage.type.backgroundColor())
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
                bottomBarMessage.actions.fastForEachIndexed { index, action ->
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            action.onClick()
                            onClose(SelectedBottomBarAction(index))
                        },
                        textColor = when (bottomBarMessage.type) {
                            BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> when (action.style) {
                                BottomBarManager.BottomBarButtonStyle.Filled -> CodeTheme.colors.error
                                BottomBarManager.BottomBarButtonStyle.Filled50 -> Color.White
                            }

                            BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.brand
                            BottomBarManager.BottomBarMessageType.REMOTE_SEND -> CodeTheme.colors.brandLight
                            BottomBarManager.BottomBarMessageType.WARNING -> Color.Black
                            BottomBarManager.BottomBarMessageType.SUCCESS -> Color.Black
                        },
                        buttonState = when (action.style) {
                            BottomBarManager.BottomBarButtonStyle.Filled -> ButtonState.Filled
                            BottomBarManager.BottomBarButtonStyle.Filled50 -> ButtonState.Filled50
                        },
                        text = action.text
                    )
                }
                if (bottomBarMessage.showCancel) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rememberedClickable {
                                onClose(SelectedBottomBarAction(-1))
                            }
                            .padding(vertical = CodeTheme.dimens.grid.x3),
                        style = CodeTheme.typography.textMedium,
                        textAlign = TextAlign.Center,
                        color = when (bottomBarMessage.type) {
                            BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                            BottomBarManager.BottomBarMessageType.REMOTE_SEND -> White

                            BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.textSecondary
                            BottomBarManager.BottomBarMessageType.SUCCESS -> White
                            BottomBarManager.BottomBarMessageType.WARNING -> White
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

@Composable
private fun BottomBarManager.BottomBarMessageType.backgroundColor(): Color = when (this) {
    BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> CodeTheme.colors.bannerError
    BottomBarManager.BottomBarMessageType.REMOTE_SEND -> CodeTheme.colors.brandLight
    BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.brand
    BottomBarManager.BottomBarMessageType.WARNING -> CodeTheme.colors.bannerWarning
    BottomBarManager.BottomBarMessageType.SUCCESS -> CodeTheme.colors.bannerSuccess
}