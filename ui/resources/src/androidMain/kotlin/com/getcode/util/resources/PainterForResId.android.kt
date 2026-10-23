package com.getcode.util.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
actual fun painterForResId(id: Int): Painter = painterResource(id)
