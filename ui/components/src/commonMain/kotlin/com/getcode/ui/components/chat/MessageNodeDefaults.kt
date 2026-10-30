package com.getcode.ui.components.chat

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

object MessageNodeDefaults {

    val DefaultShape: CornerBasedShape
        @Composable get() = CodeTheme.shapes.small

    private val PreviousSameShapeIncoming: CornerBasedShape
        @Composable get() = DefaultShape.copy(topStart = CornerSize(3.dp))

    private val NextSameShapeIncoming: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            bottomStart = CornerSize(3.dp),
            topStart = CornerSize(3.dp)
        )

    private val MiddleSameShapeIncoming: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            topStart = CornerSize(3.dp),
            bottomStart = CornerSize(3.dp)
        )

    private val PreviousSameShapeOutgoing: CornerBasedShape
        @Composable get() = DefaultShape.copy(topEnd = CornerSize(3.dp))

    private val NextSameShapeOutgoing: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            bottomEnd = CornerSize(3.dp),
            topEnd = CornerSize(3.dp)
        )

    private val MiddleSameShapeOutgoing: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            bottomEnd = CornerSize(3.dp),
            topEnd = CornerSize(3.dp)
        )

    @Composable
    fun messageShape(
        isIncoming: Boolean,
        isPreviousInGroup: Boolean,
        isNextInGroup: Boolean,
    ): Shape {
        return if (isIncoming) {
            when {
                isPreviousInGroup && isNextInGroup -> MiddleSameShapeIncoming
                isPreviousInGroup -> PreviousSameShapeIncoming
                isNextInGroup -> NextSameShapeIncoming
                else -> DefaultShape.copy(topStart = CornerSize(3.dp))
            }
        } else {
            when {
                isPreviousInGroup && isNextInGroup -> MiddleSameShapeOutgoing
                isPreviousInGroup -> PreviousSameShapeOutgoing
                isNextInGroup -> NextSameShapeOutgoing
                else -> DefaultShape.copy(topEnd = CornerSize(3.dp))
            }
        }
    }

    val ContentStyle: TextStyle
        @Composable get() = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
}
