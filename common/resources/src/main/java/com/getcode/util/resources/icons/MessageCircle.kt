package com.getcode.util.resources.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private var _MessageCircle: ImageVector? = null
val MessageCircle: ImageVector
    get() {
        if (_MessageCircle != null) {
            return _MessageCircle!!
        }
        _MessageCircle = ImageVector.Builder(
            name = "MessageCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFFFFFFFF)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7.9f, 20f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 4f, 16.1f)
                lineTo(2f, 22f)
                close()
            }
        }.build()
        return _MessageCircle!!
    }

val AutoMirroredMessageCircle: ImageVector
    @Composable get() = ImageVector.copyFrom(MessageCircle, mirror = LocalLayoutDirection.current == LayoutDirection.Ltr)

val MirroredMessageCircle: ImageVector
    get() = ImageVector.copyFrom(MessageCircle, mirror = true)