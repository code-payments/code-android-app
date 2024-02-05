package com.getcode.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import com.getcode.CodeAppState
import com.getcode.manager.BottomBarManager
import com.getcode.ui.utils.rememberedClickable
import java.util.*
import kotlin.concurrent.timerTask

@Composable
fun BottomBarContainer(appState: CodeAppState) {
    val bottomBarMessage by appState.bottomBarMessage.observeAsState()
    val bottomBarVisibleState = remember { MutableTransitionState(false) }
    var bottomBarMessageDismissId by remember { mutableLongStateOf(0L) }
    val onClose: (bottomBarActionType: BottomBarManager.BottomBarActionType?) -> Unit = {
        bottomBarMessageDismissId = bottomBarMessage?.id ?: 0
        bottomBarVisibleState.targetState = false

        bottomBarMessage?.onClose?.invoke(it)

        Timer().schedule(timerTask {
            BottomBarManager.setMessageShown(bottomBarMessageDismissId)
        }, 100)
    }
    val onBackPressed = {
        if (bottomBarMessage?.isDismissible == true) onClose(null)
    }

    if (!bottomBarVisibleState.targetState && !bottomBarVisibleState.currentState) {
        Timer().schedule(timerTask {
            bottomBarVisibleState.targetState = bottomBarMessage != null
        }, 50)

        if (bottomBarMessageDismissId == bottomBarMessage?.id) {
            onClose(null)
            bottomBarMessageDismissId = 0
        }
    }

    if (bottomBarVisibleState.targetState && bottomBarMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rememberedClickable(indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    if (bottomBarMessage?.isDismissible == true) onClose(null)
                }
        )
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
        BottomBarView(bottomBarMessage = bottomBarMessage, onClose, onBackPressed)
    }

    LaunchedEffect(bottomBarMessage) {
        bottomBarMessage?.timeoutSeconds?.let { timeout ->
            Timer().schedule(timerTask {
                onClose(null)
            }, timeout.toLong() * 1000)
        }
    }
}