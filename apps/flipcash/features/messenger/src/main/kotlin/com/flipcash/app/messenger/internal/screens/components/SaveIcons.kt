package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Save Photo: an arrow dropping into an open tray, the shape of iOS's `square.and.arrow.down`. */
internal val SavePhotoIcon: ImageVector by lazy {
    saveIcon("SavePhoto") {
        // The tray, open at the top where the arrow enters.
        moveTo(9.5f, 8.5f)
        horizontalLineTo(7.5f)
        corner(4f, 12f, r = 3.5f)
        verticalLineTo(18f)
        corner(7.5f, 21.5f, r = 3.5f)
        horizontalLineTo(16.5f)
        corner(20f, 18f, r = 3.5f)
        verticalLineTo(12f)
        corner(16.5f, 8.5f, r = 3.5f)
        horizontalLineTo(14.5f)
        arrow(x = 12f, top = 2f, tip = 15f, head = 3.5f)
    }
}

/**
 * Save All: Save Photo's tray with a second tray behind it, the shape of iOS's
 * `square.and.arrow.down.on.square`. Only the back tray's right and bottom edges show.
 */
internal val SaveAllIcon: ImageVector by lazy {
    saveIcon("SaveAll") {
        // Front tray.
        moveTo(6f, 6.5f)
        horizontalLineTo(5.5f)
        corner(2.5f, 9.5f)
        verticalLineTo(14.5f)
        corner(5.5f, 17.5f)
        horizontalLineTo(11.5f)
        corner(14.5f, 14.5f)
        verticalLineTo(9.5f)
        corner(11.5f, 6.5f)
        horizontalLineTo(11f)
        arrow(x = 8.5f, top = 1.5f, tip = 12.5f, head = 3f)
        // Back tray, from its top-right corner round to its bottom-left one.
        moveTo(17.5f, 12f)
        corner(20.5f, 15f, clockwise = true)
        verticalLineTo(20f)
        corner(17.5f, 23f, clockwise = true)
        horizontalLineTo(11.5f)
        corner(8.5f, 20f, clockwise = true)
    }
}

private fun saveIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }.build()

/**
 * A rounded corner ending at ([x], [y]). [clockwise] is the direction the outline is being drawn
 * in: the front trays go anticlockwise, the back tray clockwise. The wrong one bows the corner inward.
 */
private fun PathBuilder.corner(x: Float, y: Float, r: Float = 3f, clockwise: Boolean = false) =
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = clockwise, x1 = x, y1 = y)

private fun PathBuilder.arrow(x: Float, top: Float, tip: Float, head: Float) {
    moveTo(x, top)
    verticalLineTo(tip)
    moveTo(x - head, tip - head)
    lineTo(x, tip)
    lineTo(x + head, tip - head)
}
