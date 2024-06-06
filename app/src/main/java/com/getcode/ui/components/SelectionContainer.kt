package com.getcode.ui.components

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
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedLongClickable
import com.getcode.ui.utils.swallowClicks
import com.getcode.util.vibration.LocalVibrator

class SelectionContainerState(val words: String = "") {
    var shown by mutableStateOf(false)

    val scale : State<Float>
        @Composable get() = animateFloatAsState(
        targetValue = if (shown) 1.2f else 1f,
        label = "access key scale"
    )
}

@Composable
fun rememberSelectionState(content: String) = remember(content) { SelectionContainerState(words = content) }

@Composable
fun SelectionContainer(
    modifier: Modifier = Modifier,
    state: SelectionContainerState,
    contentRect: Rect? = null,
    content: @Composable BoxScope.(() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var _contentRect: Rect by remember(contentRect) {
        mutableStateOf(contentRect ?: Rect.Zero)
    }

    val vibrator = LocalVibrator.current
    val toolbar: TextToolbar = LocalTextToolbar.current
    val clipboard = LocalClipboardManager.current
    val copyAndToast = {
        Toast.makeText(context, "Copied to clipboard.", Toast.LENGTH_LONG).show()
        clipboard.setText(AnnotatedString(state.words))
        state.shown = false
    }

    val onClick = {
        vibrator.vibrate()
        toolbar.showMenu(
            rect = _contentRect,
            onCopyRequested = { copyAndToast() }
        )
        state.shown = true
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .addIf(contentRect == null) {
                    Modifier
                        .onPlaced { _contentRect = it.boundsInParent() }
                        .rememberedLongClickable {
                            onClick()
                        }
                },
        ) {
            content(onClick)

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
