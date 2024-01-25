package com.getcode.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.skydoves.cloudy.CloudyState

@Composable
fun Cloudy(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @androidx.annotation.IntRange(from = 0, to = 25) radius: Int = 25,
    key1: Any? = null,
    key2: Any? = null,
    allowAccumulate: (CloudyState) -> Boolean = { false },
    onStateChanged: (CloudyState) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    if (enabled) {
        com.skydoves.cloudy.Cloudy(modifier, radius, key1, key2, allowAccumulate, onStateChanged, content)
    } else {
        Box(modifier) { content() }
    }
}