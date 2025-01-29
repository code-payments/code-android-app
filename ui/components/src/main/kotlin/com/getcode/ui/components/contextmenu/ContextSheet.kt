package com.getcode.ui.components.contextmenu

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter


interface ContextMenuAction {
    val onSelect: () -> Unit

    @get:Composable
    val title: String

    @get:Composable
    val painter: Painter
    val isDestructive: Boolean
    val delayUponSelection: Boolean
}