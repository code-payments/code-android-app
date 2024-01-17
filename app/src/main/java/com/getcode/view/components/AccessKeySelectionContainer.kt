package com.getcode.view.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import com.getcode.util.rememberedLongClickable
import com.getcode.util.swallowClicks
import com.getcode.util.vibration.LocalVibrator

class AccessKeySelectionContainerState(val words: String = "") {
    var shown by mutableStateOf(false)

    val scale : State<Float>
        @Composable get() = animateFloatAsState(
        targetValue = if (shown) 1.2f else 1f,
        label = "access key scale"
    )
}

@Composable
fun rememberSelectionState(words: String) = remember(words) { AccessKeySelectionContainerState(words = words) }

@Composable
fun AccessKeySelectionContainer(
    modifier: Modifier = Modifier,
    state: AccessKeySelectionContainerState,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    var contentRect by remember {
        mutableStateOf(Rect.Zero)
    }

    val vibrator = LocalVibrator.current
    val toolbar: TextToolbar = LocalTextToolbar.current
    val clipboard = LocalClipboardManager.current
    val copyAndToast = {
        Toast.makeText(context, "Copied to clipboard.", Toast.LENGTH_LONG).show()
        clipboard.setText(AnnotatedString(state.words))
        state.shown = false
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .onPlaced { contentRect = it.boundsInParent() }
                .rememberedLongClickable {
                    vibrator.vibrate()
                    toolbar.showMenu(
                        rect = contentRect,
                        onCopyRequested = { copyAndToast() }
                    )
                    state.shown = true
                },
        ) {
            content()
        }

        if (state.shown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .swallowClicks {
                        state.shown = false
                        toolbar.hide()
                    }
            )
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            state.shown = false
            toolbar.hide()
        }
    }
}