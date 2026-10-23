package com.getcode.ui.core

import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.swallowClicks(onClick: () -> Unit = { }): Modifier =
    this.pointerInteropFilter {
        if (it.actionMasked == MotionEvent.ACTION_UP) {
            onClick()
        }
        true
    }
