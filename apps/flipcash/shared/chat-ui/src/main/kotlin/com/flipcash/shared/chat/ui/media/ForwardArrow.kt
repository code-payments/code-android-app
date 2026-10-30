package com.flipcash.shared.chat.ui.media

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

// Material has no forward counterpart to Reply, and its Forward is a straight block arrow. iOS draws
// Forward as Reply's mirror (arrowshape.turn.up.right.fill), so this is Reply's path flipped across x = 12.
val ForwardArrow: ImageVector by lazy {
    materialIcon(name = "Flipcash.ForwardArrow", autoMirror = true) {
        materialPath {
            moveTo(14.0f, 9.0f)
            verticalLineTo(5.0f)
            lineToRelative(7.0f, 7.0f)
            lineToRelative(-7.0f, 7.0f)
            verticalLineToRelative(-4.1f)
            curveToRelative(-5.0f, 0.0f, -8.5f, 1.6f, -11.0f, 5.1f)
            curveToRelative(1.0f, -5.0f, 4.0f, -10.0f, 11.0f, -11.0f)
            close()
        }
    }
}
