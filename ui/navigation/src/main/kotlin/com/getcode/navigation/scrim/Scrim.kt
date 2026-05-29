package com.getcode.navigation.scrim

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.noRippleClickable

class ScrimController {
    var visible by mutableStateOf(false)
        private set

    private var onDismiss: (() -> Unit)? = null
    var overlayAlignment: Alignment by mutableStateOf(Alignment.Center)
        private set
    var overlayContent: (@Composable () -> Unit)? by mutableStateOf(null)
        private set
    var enterTransition: EnterTransition by mutableStateOf(fadeIn())
        private set
    var exitTransition: ExitTransition by mutableStateOf(fadeOut())
        private set


    fun show(
        content: (@Composable () -> Unit)? = null,
        enterTransition: EnterTransition = fadeIn(),
        exitTransition: ExitTransition = fadeOut(),
        alignment: Alignment = Alignment.Center,
        onDismiss: () -> Unit,
    ) {
        this.onDismiss = onDismiss
        this.overlayContent = content
        this.overlayAlignment = alignment
        this.enterTransition = enterTransition
        this.exitTransition = exitTransition
        visible = true
    }

    private fun hide() {
        visible = false
        onDismiss = null
        overlayContent = null

    }

    fun dismiss() {
        onDismiss?.invoke()
        hide()
    }
}

val LocalScrimController = staticCompositionLocalOf<ScrimController> {
    error("No ScrimController provided")
}

@Composable
fun ScrimOverlay(controller: ScrimController) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = controller.visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CodeTheme.colors.scrim)
                    .noRippleClickable { controller.dismiss() },
            )
        }

        AnimatedContent(
            modifier = Modifier.align(controller.overlayAlignment),
            targetState = controller.overlayContent,
            transitionSpec = {
                controller.enterTransition togetherWith controller.exitTransition
            }
        ) { content ->
            if (content != null) {
                content()
            } else {
                Spacer(Modifier.fillMaxWidth())
            }
        }
    }
}