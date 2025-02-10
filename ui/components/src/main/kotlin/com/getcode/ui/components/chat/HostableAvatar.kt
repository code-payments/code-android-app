package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.utils.heightOrZero
import com.getcode.ui.utils.widthOrZero
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

sealed interface AvatarEndAction {
    val backgroundColor: Color
    val contentColor: Color

    data class Icon(
        val icon: Painter,
        override val backgroundColor: Color,
        override val contentColor: Color
    ) : AvatarEndAction
}

@Composable
fun HostableAvatar(
    imageData: Any?,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    size: Dp = CodeTheme.dimens.staticGrid.x8,
    endAction: AvatarEndAction? = null,
    isHost: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {
        Image(
            modifier = Modifier.padding(5.dp),
            imageVector = Icons.Default.Person,
            colorFilter = ColorFilter.tint(Color.White),
            contentDescription = null,
        )
    }
) {
    Layout(
        modifier = modifier.graphicsLayer { clip = false },
        content = {
            UserAvatar(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .layoutId("content"),
                data = imageData,
                overlay = overlay
            )

            if (isHost) {
                Image(
                    modifier = imageModifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .layoutId("crown")
                        .size(getBadgeSize(size))
                        .background(color = Color(0xFFE9C432), shape = CircleShape)
                        .padding(4.dp),
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(CodeTheme.colors.brand)
                )
            }

            if (endAction != null) {
                when (endAction) {
                    is AvatarEndAction.Icon -> {
                        Image(
                            modifier = imageModifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .layoutId("badge")
                                .size(getBadgeSize(size))
                                .background(color = endAction.backgroundColor, shape = CircleShape)
                                .padding(6.dp),
                            painter = endAction.icon,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(endAction.contentColor)
                        )
                    }
                }
            }
        }
    ) { measurables, incomingConstraints ->
        val constraints = incomingConstraints.copy(minWidth = 0, minHeight = 0)
        val contentPlaceable =
            measurables.find { it.layoutId == "content" }?.measure(constraints)
        val crownPlaceable =
            measurables.find { it.layoutId == "crown" }?.measure(constraints)
        val badgePlaceable =
            measurables.find { it.layoutId == "badge" }?.measure(constraints)

        val maxWidth = widthOrZero(contentPlaceable)
        val maxHeight = heightOrZero(contentPlaceable)

        layout(width = maxWidth, height = maxHeight) {
            contentPlaceable?.placeRelative(0, 0)

            val avatarSizePx = widthOrZero(contentPlaceable)
            val avatarRadius = avatarSizePx / 2f

            crownPlaceable?.let { crown ->
                val offset = placeBadgeOnAvatarPerimeter(
                    placeable = crown,
                    avatarRadius = avatarRadius,
                    centerX = avatarRadius,
                    centerY = avatarRadius,
                    angleDegrees = 225f
                )
                crown.placeRelative(offset.x, offset.y)
            }

            badgePlaceable?.let { badge ->
                val offset = placeBadgeOnAvatarPerimeter(
                    placeable = badge,
                    avatarRadius = avatarRadius,
                    centerX = avatarRadius,
                    centerY = avatarRadius,
                    angleDegrees = 315f
                )
                badge.placeRelative(offset.x, offset.y)
            }
        }
    }
}

private fun getBadgeSize(x: Dp): Dp {
    // Using the “point-slope” approach
    return 20.dp + (x - 40.dp) * (5f / 35f)
    // which simplifies to (x / 7) + (100 / 7)
}

private fun MeasureScope.placeBadgeOnAvatarPerimeter(
    placeable: Placeable?,
    avatarRadius: Float,
    centerX: Float,
    centerY: Float,
    angleDegrees: Float
): IntOffset {
    val angle = Math.toRadians(angleDegrees.toDouble()).toFloat()

    val badgeRadius = widthOrZero(placeable) / 2f

    val distanceBetweenCenters = avatarRadius + badgeRadius - (avatarRadius * 0.30f)

    val badgeCenterX = centerX + distanceBetweenCenters * cos(angle)
    val badgeCenterY = centerY + distanceBetweenCenters * sin(angle)

    val offsetX = badgeCenterX - badgeRadius
    val offsetY = badgeCenterY - badgeRadius + 2.dp.toPx()

    return IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
}