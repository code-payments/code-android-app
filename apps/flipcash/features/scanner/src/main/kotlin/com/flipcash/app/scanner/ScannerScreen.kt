package com.flipcash.app.scanner

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.scanner.internal.Scanner

@Composable
fun ScannerScreen() {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        activity?.reportFullyDrawn()
    }
    Scanner()
}
