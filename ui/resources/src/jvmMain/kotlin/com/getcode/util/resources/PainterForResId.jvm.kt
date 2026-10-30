package com.getcode.util.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun painterForResId(id: Int): Painter = painterResource(ComposeResourceIds.drawable(id))
