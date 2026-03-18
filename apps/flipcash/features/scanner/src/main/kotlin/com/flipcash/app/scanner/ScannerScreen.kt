package com.flipcash.app.scanner

import androidx.compose.runtime.Composable
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.scanner.internal.Scanner

@Composable
fun ScannerScreen(deepLink: DeeplinkType? = null) {
    Scanner(deepLink)
}
