package com.getcode.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import timber.log.Timber

@Composable
fun OnWindowFocusedRequester(
    focusRequester: FocusRequester,
) {
    // initiallyFocused: utilize a LaunchedEffect to gain focus the FIRST time the window is focused
    val windowInfo = LocalWindowInfo.current
    var focusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused && !focusRequested) {
            try {
                focusRequested = true
                focusRequester.requestFocus()
            } catch (ex: IllegalStateException) {
                // One case observed in staging. For some reason reported "FocusRequester is not
                // initialized."
                Timber.e(ex, "Failed to set focus to text field")
            }
        }
    }
}