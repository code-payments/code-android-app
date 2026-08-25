package com.getcode.ui.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@Deprecated(
    message = "Replaced with KeyboardController that combines visibility with show/hide support"
)
fun keyboardAsState(): State<Boolean> {
    val keyboardState = remember { mutableStateOf(false) }
    val view = LocalView.current
    val viewTreeObserver = view.viewTreeObserver
    DisposableEffect(viewTreeObserver) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            keyboardState.value = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) ?: true
        }
        viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return keyboardState
}

class KeyboardController(
    private val view: View,
    private val softwareController: SoftwareKeyboardController?,
    private val focusManager: FocusManager,
    private val coroutineScope: CoroutineScope,
) {
    var visible by mutableStateOf(false)
        private set

    fun show() {
        softwareController?.show()
    }

    /**
     * Takes the keyboard down and *keeps* it down: clears editor focus, then hides the IME.
     *
     * Hiding on its own isn't enough. The field stays focused, so the platform brings the keyboard
     * straight back — most visibly when resuming from the background, where the window restores the
     * IME for whatever still holds focus. Clearing focus removes the target it would be restored
     * onto.
     *
     * Unconditional because every caller is taking the keyboard down on the way somewhere else: a
     * pop, a sheet dismissal, a flow step, a deeplink being routed. A screen that wants the IME
     * down while the field stays armed needs its own FocusRequester rather than this.
     */
    fun hide() {
        focusManager.clearFocus(force = true)
        softwareController?.hide()
    }

    fun restartInput() {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.restartInput(view)
    }

    fun hideIfVisible(block: () -> Unit = { }) {
        coroutineScope.launch {
            if (visible) {
                hide()
                delay(300)
            }
            block()
        }
    }

    // Internal setup for visibility tracking
    @SuppressLint("ComposableNaming")
    @Composable
    internal fun setupVisibilityTracking() {
        val viewTreeObserver = view.viewTreeObserver
        DisposableEffect(viewTreeObserver) {
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                visible = ViewCompat.getRootWindowInsets(view)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
            }
            viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose { viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
    }
}

@Composable
fun rememberKeyboardController(): KeyboardController {
    val view = LocalView.current
    val softwareController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val keyboardController = remember(view, softwareController, focusManager) {
        KeyboardController(view, softwareController, focusManager, scope)
    }

    // Trigger visibility tracking
    keyboardController.setupVisibilityTracking()

    return keyboardController
}

