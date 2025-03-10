package com.getcode.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

interface ContextMenuAction {
    interface Single: ContextMenuAction {
        val onSelect: () -> Unit

        @get:Composable
        val title: String

        @get:Composable
        val painter: Painter
        val isDestructive: Boolean
        val delayUponSelection: Boolean
    }

    @Suppress("PropertyName")
    interface Custom: ContextMenuAction {
        val Content: @Composable () -> Unit
    }
}