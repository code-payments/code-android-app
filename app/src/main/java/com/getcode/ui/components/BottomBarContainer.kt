package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.getcode.CodeAppState
import com.getcode.manager.BottomBarManager
import com.getcode.ui.utils.rememberedClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BottomBarContainer(appState: CodeAppState) {
    val scope = rememberCoroutineScope()
    val bottomBarMessage by appState.bottomBarMessage.collectAsState()
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