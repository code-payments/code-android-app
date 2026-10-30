package com.getcode.util.resources

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Loads the drawable behind a resource ID handed out by [ResourceHelper.getIdentifier].
 *
 * Shared components take currency and flag icons as `Int` IDs; on Android these are `R.drawable`
 * values, on desktop they index the generated Compose resources.
 */
@Composable
expect fun painterForResId(@DrawableRes id: Int): Painter
