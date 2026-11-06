package com.flipcash.shared.chat.ui.media

// kmp-audit: draws through PhotoLayers and Coil's AsyncImage, which sit in androidMain with the photo bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
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

/**
 * What a card behind the front one draws: the photo of another message in the stack. [localModel]
 * is the staged file of a photo still sending, which has no stored blob for [model] yet.
 */
class StackCardPhoto(val blurHash: String?, val model: Any?, val localModel: String? = null)

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
 * A stack's card sizes in its frame: the front photo [frontWidth] wide at the frame's full height,
 * and each back card [frontWidth] by [cardHeight].
 */
internal data class StackFan(val frontWidth: Float, val cardHeight: Float)

/**
 * Where the cards of a stack sit inside its square frame, laid out as iOS lays them out.
 *
 * The frame is a single photo's width, so the stack clears the avatar gutter and the timestamp column
 * as a single photo does. The front photo keeps the frame's height and narrows; the tilted, nudged
 * cards behind it are shortened until their corners just meet the frame's edges.
 */
internal object PhotoStackGeometry {
    /**
     * The poses of [behind] cards, nearest first. The near one leans out to the leading side and the
     * far one to the trailing side, as in the design; a stack of two draws only the near one.
     */
    fun poses(behind: Int, nearNudge: Float, farNudge: Float): List<StackCardPose> =
        listOf(StackCardPose(-NEAR_TILT, -nearNudge), StackCardPose(FAR_TILT, farNudge)).take(behind)

    /**
     * Sizes a stack's cards for a [width] by [height] frame, as iOS's `stackFan` does: the front photo
     * keeps the full height and narrows, centred, and each back card is as wide as the front and
     * short enough that, turned and nudged by its pose, its corners stay inside the frame. Null when
     * the frame is too narrow or flat to fan; the cards then fill it.
     */
    fun fan(width: Float, height: Float, poses: List<StackCardPose>): StackFan? {
        if (width <= 0f || height <= 0f) return null
        // A w by h card turned by the tilt spans w·cos + h·sin across, less the nudge on each side,
        // and w·sin + h·cos down.
        class Span(val cos: Float, val sin: Float, val across: Float)
        val spans = poses.map { pose ->
            val radians = Math.toRadians(pose.tiltDegrees.toDouble())
            Span(abs(cos(radians)).toFloat(), abs(sin(radians)).toFloat(), width - 2f * abs(pose.nudge))
        }
        // The widest front for which every pose's card can still meet both edges.
        var front = width
        for (span in spans) {
            val determinant = span.cos * span.cos - span.sin * span.sin
            if (determinant <= 0f) return null
            front = min(front, (span.across * span.cos - height * span.sin) / determinant)
        }
        if (front <= 0f) return null
        var card = height
        for (span in spans) {
            card = min(card, (height - front * span.sin) / span.cos)
            if (span.sin > 0f) card = min(card, (span.across - front * span.cos) / span.sin)
        }
        return if (card > 0f) StackFan(frontWidth = front, cardHeight = card) else null
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
 * The space around [look]'s front card inside the [width] by [height] frame the stack takes: what
 * something drawn on the front card, such as a reply's quote, insets by.
 */
@Composable
internal fun photoStackFrontPadding(look: PhotoStackLook, width: Float, height: Float): PaddingValues {
    val front = PhotoStackGeometry.fan(width, height, photoStackPoses(look))?.frontWidth ?: width
    return PaddingValues(horizontal = ((width - front) / 2f).dp)
}

/**
 * Draws [look]'s cards behind [content], the front card, in a [width] by [height] frame (a square,
 * from [ChatMediaBubbleSizing.stackSize]). [content] receives the size to draw the front card at;
 * [frontShape] is the shape it draws in, which its outline and shadow follow.
 */
@Composable
internal fun PhotoStackFrame(
    look: PhotoStackLook,
    width: Float,
    height: Float,
    frontShape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable (cardWidth: Float, cardHeight: Float) -> Unit,
) {
    val poses = photoStackPoses(look)
    val fan = PhotoStackGeometry.fan(width, height, poses)
    val cardWidth = fan?.frontWidth ?: width
    val cardHeight = fan?.cardHeight ?: height
    val radius = RoundedCornerShape(BubbleDefaults.cornerLarge)
    Box(modifier.size(width.dp, height.dp), contentAlignment = Alignment.Center) {
        // Farthest first, so each nearer card covers the last.
        look.behind.zip(poses).asReversed().forEach { (card, pose) ->
            Box(
                Modifier
                    .size(cardWidth.dp, cardHeight.dp)
                    .offset(x = pose.nudge.dp)
                    // Clipped after the turn and the nudge, so the clip turns with the card.
                    .graphicsLayer { rotationZ = pose.tiltDegrees }
                    .clip(radius)
                    .testTag(PHOTO_STACK_CARD_TAG),
            ) {
                PhotoStackCardInset(radius) {
                    PhotoLayers(
                        blurHash = card.blurHash,
                        model = card.model,
                        localModel = card.localModel,
                        sentPreview = null,
                        blurhashOnly = card.model == null && card.localModel == null,
                        onLoadError = null,
                    )
                    // Dimmed so the front card reads as the one in the light.
                    Box(Modifier.fillMaxSize().background(CodeTheme.colors.scrim))
                }
            }
        }
        // The front card's shadow falls on the cards behind it, so cards of photos that look alike
        // still read apart. [content] draws the front photo inside its own outline.
        // The front photo keeps the frame's full height.
        Box(Modifier.size(cardWidth.dp, height.dp).dropShadow(frontShape, PhotoStackFrontShadow)) {
            content(cardWidth, height)
        }
    }
}

/**
 * A stack card's outline and photo: the card is filled with the conversation's background colour,
 * and [content] is clipped to [shape] [PHOTO_STACK_OUTLINE_WIDTH] inside its edge, so the frame keeps
 * its size. The photo's anti-aliased edge falls on the outline rather than on the card's own edge,
 * where it would show past the outline as a line of the photo's colour.
 */
@Composable
internal fun PhotoStackCardInset(shape: Shape, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background)
            .padding(PHOTO_STACK_OUTLINE_WIDTH)
            .clip(shape),
        content = content,
    )
}

private val PHOTO_STACK_OUTLINE_WIDTH = 3.dp

private val PhotoStackFrontShadow = Shadow(
    radius = 8.dp,
    color = Color.Black,
    offset = DpOffset(0.dp, 3.dp),
    alpha = 0.7f,
)

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
