package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Save All: the filled Download glyph Save Photo uses, raised to make room for a second tray under
 * the first, so it reads as saving several photos. Material has no save-many glyph; a photo-library
 * or stacked-cards icon reads as viewing the photos or copying them.
 */
internal val SaveAllIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SaveAll",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Material's filled Download, 1.5 higher.
            moveTo(19f, 7.5f)
            horizontalLineToRelative(-4f)
            verticalLineTo(1.5f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineTo(5f)
            lineToRelative(7f, 7f)
            lineToRelative(7f, -7f)
            close()
            moveTo(5f, 16.5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            close()
            // The second tray.
            moveTo(5f, 20.5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            close()
        }
    }.build()
}
