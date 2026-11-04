package com.flipcash.shared.chat.ui.media

// kmp-audit: draws through PhotoLayers and Coil's AsyncImage, which sit in androidMain with the photo bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_chat_media_stackCount
import com.flipcash.shared.chat.ui.BubbleDefaults
import com.getcode.theme.Black50
import com.getcode.theme.CodeTheme
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal const val PHOTO_STACK_PILL_TAG = "chat_photo_stack_pill"
internal const val PHOTO_STACK_CARD_TAG = "chat_photo_stack_card"

/** What a card behind the front one draws: the photo of another message in the stack. */
class StackCardPhoto(val blurHash: String?, val model: Any?)

/**
 * The look of a card stack: [count] photos sent together, with [behind] the cards fanned behind the
 * front one, nearest first. There is one card behind for each photo after the first, up to two.
 */
class PhotoStackLook(val count: Int, val behind: List<StackCardPhoto>) {
    companion object {
        /** The most cards drawn behind the front one, however many photos the stack holds. */
        const val MAX_BEHIND = 2

        /** How many cards a stack of [count] photos draws behind its front card. */
        fun behindCount(count: Int): Int = (count - 1).coerceIn(0, MAX_BEHIND)
    }
}

/** How a card behind the front one sits: tilted [tiltDegrees] about its centre and shifted [nudge] dp sideways. */
internal class StackCardPose(val tiltDegrees: Float, val nudge: Float)

/**
 * Where the cards of a stack sit inside the box a single photo bubble takes.
 *
 * The stack takes exactly that box, so it clears the avatar gutter and the timestamp column and
 * keeps the normal gap to the next message, as a single photo does. The fan has to fit inside it,
 * so every card, the front one included, is scaled down until the tilted, nudged cards behind it
 * no longer reach past the box's edges.
 */
internal object PhotoStackGeometry {
    /**
     * The poses of [behind] cards, nearest first. The near one leans out to the leading side and the
     * far one to the trailing side, as in the design; a stack of two draws only the near one.
     */
    fun poses(behind: Int, nearNudge: Float, farNudge: Float): List<StackCardPose> =
        listOf(StackCardPose(-NEAR_TILT, -nearNudge), StackCardPose(FAR_TILT, farNudge)).take(behind)

    /**
     * The fraction of [width] by [height] each card is drawn at so that every pose in [poses],
     * turned about the card's centre, stays inside the box. 1 with nothing behind.
     */
    fun cardScale(width: Float, height: Float, poses: List<StackCardPose>): Float {
        if (width <= 0f || height <= 0f) return 1f
        var scale = 1f
        for (pose in poses) {
            val radians = Math.toRadians(pose.tiltDegrees.toDouble())
            val c = abs(cos(radians)).toFloat()
            val s = abs(sin(radians)).toFloat()
            // A w by h card turned by the tilt spans w·cos + h·sin across and w·sin + h·cos down;
            // the nudge spends some of the width on one side.
            val across = (width - 2f * abs(pose.nudge)) / (width * c + height * s)
            val down = height / (width * s + height * c)
            scale = min(scale, min(across, down))
        }
        return scale.coerceIn(0f, 1f)
    }

    /**
     * The height of the stack's box once its cards are [cardWidth] by [cardHeight]: the tallest
     * card's span down, turned by its pose. No taller than the single photo's box when the cards come
     * from [cardScale], and shorter when the width was what shrank them, so the stack takes no empty
     * band above or below the fan.
     */
    fun stackHeight(cardWidth: Float, cardHeight: Float, poses: List<StackCardPose>): Float =
        poses.fold(cardHeight) { tallest, pose ->
            val radians = Math.toRadians(pose.tiltDegrees.toDouble())
            val down = cardWidth * abs(sin(radians)).toFloat() + cardHeight * abs(cos(radians)).toFloat()
            maxOf(tallest, down)
        }

    private const val NEAR_TILT = 4f
    private const val FAR_TILT = 6f
}

/** The poses of [look]'s cards behind the front one, in the theme's grid. */
@Composable
internal fun photoStackPoses(look: PhotoStackLook): List<StackCardPose> = PhotoStackGeometry.poses(
    behind = look.behind.size,
    nearNudge = CodeTheme.dimens.staticGrid.x1.value,
    farNudge = CodeTheme.dimens.staticGrid.x2.value,
)

/**
 * The space around [look]'s front card inside the [width] by [height] box the stack takes: what
 * something drawn on the front card, such as a reply's quote, insets by.
 */
@Composable
internal fun photoStackFrontPadding(look: PhotoStackLook, width: Float, height: Float): PaddingValues {
    val poses = photoStackPoses(look)
    val scale = PhotoStackGeometry.cardScale(width, height, poses)
    val stackHeight = PhotoStackGeometry.stackHeight(width * scale, height * scale, poses)
    return PaddingValues(
        horizontal = (width * (1f - scale) / 2f).dp,
        vertical = ((stackHeight - height * scale) / 2f).dp,
    )
}

/**
 * Draws [look]'s cards behind [content], the front card, inside the width a single photo bubble of
 * this photo takes ([width] by [height]) and no taller than it. [content] receives the size to draw the front card at.
 */
@Composable
internal fun PhotoStackFrame(
    look: PhotoStackLook,
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    content: @Composable (cardWidth: Float, cardHeight: Float) -> Unit,
) {
    val poses = photoStackPoses(look)
    val scale = PhotoStackGeometry.cardScale(width, height, poses)
    val cardWidth = width * scale
    val cardHeight = height * scale
    val radius = RoundedCornerShape(BubbleDefaults.cornerLarge)
    // As wide as the single photo, and only as tall as the fan, so nothing sits above or below it.
    val stackHeight = PhotoStackGeometry.stackHeight(cardWidth, cardHeight, poses)
    Box(modifier.size(width.dp, stackHeight.dp), contentAlignment = Alignment.Center) {
        // Farthest first, so each nearer card covers the last.
        look.behind.zip(poses).asReversed().forEach { (card, pose) ->
            Box(
                Modifier
                    .size(cardWidth.dp, cardHeight.dp)
                    .offset(x = pose.nudge.dp)
                    .graphicsLayer { rotationZ = pose.tiltDegrees }
                    .clip(radius)
                    .testTag(PHOTO_STACK_CARD_TAG),
            ) {
                PhotoLayers(
                    blurHash = card.blurHash,
                    model = card.model,
                    localModel = null,
                    sentPreview = null,
                    blurhashOnly = card.model == null,
                    onLoadError = null,
                )
                // Dimmed so the front card reads as the one in the light.
                Box(Modifier.fillMaxSize().background(CodeTheme.colors.scrim))
            }
        }
        content(cardWidth, cardHeight)
    }
}

/** The "N photos" pill drawn over the front card's top corner. */
@Composable
internal fun PhotoStackPill(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(Res.plurals.label_chat_media_stackCount, count, count),
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textMain,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Black50)
            .padding(horizontal = CodeTheme.dimens.staticGrid.x2, vertical = CodeTheme.dimens.staticGrid.x1)
            .testTag(PHOTO_STACK_PILL_TAG),
    )
}
