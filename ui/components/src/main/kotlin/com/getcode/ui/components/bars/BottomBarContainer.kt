package com.getcode.ui.components.bars

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarAction.Companion.OK_DESCRIPTOR
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SelectedBottomBarAction
import com.getcode.theme.Black40
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White50
import com.getcode.ui.core.addIf
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.scaled
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
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
    val onClose: suspend (selection: SelectedBottomBarAction, fromTimeout: Boolean) -> Unit =
        { selection, fromTimeout ->
            bottomBarMessageDismissId = bottomBarMessage?.id ?: 0
            bottomBarVisibleState.targetState = false

            delay(300.scaled(animationScale))
            BottomBarManager.setMessageShown(bottomBarMessageDismissId)
            if (fromTimeout) {
                bottomBarMessage?.onTimeout?.invoke()
            } else {
                bottomBarMessage?.onClose?.invoke(selection)
            }
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
            onClose(SelectedBottomBarAction(-1), true)
        }
    }

    val scrimAlpha by animateFloatAsState(
        if (bottomBarMessage?.showScrim == true) 1f else 0f,
        label = "scrim visibility"
    )

    if (bottomBarVisibleState.targetState && bottomBarMessage != null) {
        bottomBarMessage?.let {
            if (it.showScrim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(CodeTheme.colors.scrim)
                        .rememberedClickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (it.isDismissible) {
                                scope.launch { onClose(SelectedBottomBarAction(-1), false) }
                            }
                        }
                )
            } else {
                if (it.isDismissible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rememberedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                scope.launch { onClose(SelectedBottomBarAction(-1), false) }
                            }
                    )
                }
            }
        }
    }

    AnimatedContent(
        modifier = Modifier
            .fillMaxSize()
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
                scope.launch { onClose(selection, false) }
            }
            BottomBarView(
                bottomBarMessage = bottomBarMessage,
                onClose = closeWith,
                onBackPressed = { closeWith(SelectedBottomBarAction(-1)) }
            )
        } else {
            Spacer(Modifier.fillMaxWidth())
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
                .background(
                    bottomBarMessage.type.backgroundColor(),
                    CodeTheme.shapes.medium.copy(
                        bottomStart = CornerSize(0),
                        bottomEnd = CornerSize(0)
                    )
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
                            style = CodeTheme.typography.textMedium,
                            text = bottomBarMessage.title
                        )
                        if (bottomBarMessage.subtitle.isNotEmpty()) {
                            Text(
                                style = CodeTheme.typography.textSmall,
                                text = bottomBarMessage.subtitle,
                                color = LocalContentColor.current.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            if (bottomBarMessage.additionalInfo.isNotEmpty()) {
                var isExpanded by remember { mutableStateOf(false) }

                CompositionLocalProvider(LocalContentColor provides White) {
                    Column {
                        Row(
                            modifier = Modifier.clickable { isExpanded = !isExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                            val linkPrefix = if (isExpanded) "Hide" else "View"
                            Text(
                                text = "$linkPrefix Additional Information",
                                style = CodeTheme.typography.caption,
                                color = LocalContentColor.current.copy(alpha = 0.8f),
                            )
                            Icon(
                                modifier = Modifier.rotate(rotation),
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = LocalContentColor.current.copy(alpha = 0.8f),
                            )
                        }
                        AnimatedContent(
                            targetState = isExpanded,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                slideInVertically { -it } togetherWith slideOutVertically { -it }
                            }
                        ) { expanded ->
                            if (expanded) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = """
                                    ${bottomBarMessage.additionalInfo.onEach { (k, v) -> "$k: $v" }}
                                """.trimIndent(),
                                    style = CodeTheme.typography.textSmall,
                                )
                            } else {
                                Spacer(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                val okText = stringResource(R.string.action_ok)
                val actions by remember(bottomBarMessage.actions) {
                    derivedStateOf {
                        bottomBarMessage.actions.map {
                            if (it.text == OK_DESCRIPTOR && !it.isUser) {
                                it.copy(text = okText) // ensure localized
                            } else {
                                it
                            }
                        }
                    }
                }

                actions.fastForEachIndexed { index, action ->
                    CodeButton(
                        modifier = Modifier.fillMaxWidth()
                            .addIf(index == actions.lastIndex) {
                                Modifier.padding(
                                    bottom = when (action.style) {
                                        BottomBarManager.BottomBarButtonStyle.Filled -> CodeTheme.dimens.grid.x2
                                        BottomBarManager.BottomBarButtonStyle.Filled50 -> CodeTheme.dimens.grid.x2
                                        BottomBarManager.BottomBarButtonStyle.Outlined -> CodeTheme.dimens.grid.x2
                                        BottomBarManager.BottomBarButtonStyle.Text -> 0.dp
                                    }
                                )
                            },
                        onClick = {
                            action.onClick()
                            onClose(SelectedBottomBarAction(index.takeIf { action.isUser } ?: -1))
                        },
                        textColor = when (bottomBarMessage.type) {
                            BottomBarManager.BottomBarMessageType.ERROR,
                            BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> when (action.style) {
                                BottomBarManager.BottomBarButtonStyle.Filled -> CodeTheme.colors.error
                                BottomBarManager.BottomBarButtonStyle.Filled50 -> Color.White
                                BottomBarManager.BottomBarButtonStyle.Outlined -> Color.White
                                BottomBarManager.BottomBarButtonStyle.Text -> White50
                            }

                            BottomBarManager.BottomBarMessageType.THEMED -> when (action.style) {
                                BottomBarManager.BottomBarButtonStyle.Filled -> CodeTheme.colors.brand
                                BottomBarManager.BottomBarButtonStyle.Filled50 -> CodeTheme.colors.brand
                                BottomBarManager.BottomBarButtonStyle.Outlined -> Color.White
                                BottomBarManager.BottomBarButtonStyle.Text -> White50
                            }
                            BottomBarManager.BottomBarMessageType.WARNING -> Color.Black
                            BottomBarManager.BottomBarMessageType.INFO -> when (action.style) {
                                BottomBarManager.BottomBarButtonStyle.Text -> White50
                                else -> Color.Black
                            }

                            BottomBarManager.BottomBarMessageType.SUCCESS -> Color.Black
                        },
                        buttonState = when (action.style) {
                            BottomBarManager.BottomBarButtonStyle.Filled -> ButtonState.Filled
                            BottomBarManager.BottomBarButtonStyle.Filled50 -> ButtonState.Filled50
                            BottomBarManager.BottomBarButtonStyle.Outlined -> ButtonState.Bordered
                            BottomBarManager.BottomBarButtonStyle.Text -> ButtonState.Subtle
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
                            BottomBarManager.BottomBarMessageType.ERROR,
                            BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                            BottomBarManager.BottomBarMessageType.INFO -> White50

                            BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.textSecondary
                            BottomBarManager.BottomBarMessageType.SUCCESS -> White50
                            BottomBarManager.BottomBarMessageType.WARNING -> White50
                        },
                        text = stringResource(R.string.action_cancel)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarManager.BottomBarMessageType.backgroundColor(): Color = when (this) {
    BottomBarManager.BottomBarMessageType.ERROR,
    BottomBarManager.BottomBarMessageType.DESTRUCTIVE -> CodeTheme.colors.bannerError

    BottomBarManager.BottomBarMessageType.INFO -> CodeTheme.colors.brandLight
    BottomBarManager.BottomBarMessageType.THEMED -> CodeTheme.colors.bannerThemed
    BottomBarManager.BottomBarMessageType.WARNING -> CodeTheme.colors.bannerWarning
    BottomBarManager.BottomBarMessageType.SUCCESS -> CodeTheme.colors.bannerSuccess
}