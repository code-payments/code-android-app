package com.getcode.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.cloudy

@Composable
fun Cloudy(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @androidx.annotation.IntRange(from = 0, to = 25) radius: Int = 25,
    onStateChanged: (CloudyState) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.cloudy(enabled = enabled, radius = radius, onStateChanged = onStateChanged)) {
        content()
    }
}