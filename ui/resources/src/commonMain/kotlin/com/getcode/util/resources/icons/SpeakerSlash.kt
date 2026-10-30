package com.getcode.util.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Speaker with no sound waves and a diagonal slash, matching iOS's SF Symbol `speaker.slash`.
private var _SpeakerSlash: ImageVector? = null
val SpeakerSlash: ImageVector
    get() {
        if (_SpeakerSlash != null) {
            return _SpeakerSlash!!
        }
        _SpeakerSlash = ImageVector.Builder(
            name = "SpeakerSlash",
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
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6f, 9.5f)
                horizontalLineTo(9f)
                lineTo(14f, 5f)
                verticalLineTo(19f)
                lineTo(9f, 14.5f)
                horizontalLineTo(6f)
                close()
                moveTo(4f, 4f)
                lineTo(20f, 20f)
            }
        }.build()
        return _SpeakerSlash!!
    }
