package com.flipcash.shared.chat.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.shared.chat.ui.BubbleDefaults
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.assertEquals

/**
 * Renders a two-photo stack, a tall screenshot and a wide photo, and checks that no card's photo
 * reaches past the outline inside its own rounded shape. Each photo is one saturated colour filled
 * edge to edge, so a photo pixel anywhere in a card's outline band, or outside the card, is found by
 * its colour. The render is also written to `build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class PhotoStackClipScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tallFrontWideBehind() = check("photo_stack_tall_front.png", front = TALL, behind = WIDE)

    @Test
    fun wideFrontTallBehind() = check("photo_stack_wide_front.png", front = WIDE, behind = TALL)

    private fun check(name: String, front: Photo, behind: Photo) {
        // Loads on the calling thread, so both photos are drawn by the time the frame settles.
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(composeRule.activity)
                .coroutineContext(Dispatchers.Unconfined)
                .crossfade(false)
                .build(),
        )
        var radiusDp = 0f
        var pose: StackCardPose? = null
        val look = PhotoStackLook(count = 2, behind = listOf(StackCardPhoto(blurHash = null, model = behind.bitmap())))
        composeRule.setContent {
            FlipcashTheme {
                radiusDp = BubbleDefaults.cornerLarge.value
                pose = photoStackPoses(look).single()
                Box(Modifier.fillMaxSize().background(CodeTheme.colors.background).padding(24.dp)) {
                    ChatPhotoBubble(
                        isFromSelf = false,
                        maxWidth = 280.dp,
                        imageWidth = front.width,
                        imageHeight = front.height,
                        blurHash = null,
                        model = front.bitmap(),
                        stack = look,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(File("build/screenshots").apply { mkdirs() }, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")

        val density = composeRule.activity.resources.displayMetrics.density
        val frontBounds = composeRule.onNodeWithTag(PHOTO_BUBBLE_TAG).fetchSemanticsNode().boundsInRoot
        val frontCard = Card(
            centerX = frontBounds.center.x,
            centerY = frontBounds.center.y,
            width = frontBounds.width,
            height = frontBounds.height,
            radius = radiusDp * density,
            tiltDegrees = 0f,
        )
        val backCard = frontCard.copy(
            centerX = frontCard.centerX + pose!!.nudge * density,
            tiltDegrees = pose!!.tiltDegrees,
        )
        // The photo stops at the outline's inner edge; allow its anti-aliased pixel and one more.
        val inset = OUTLINE_DP * density - 2f
        val strays = strayPixels(bitmap, front.channel, frontCard, inset) +
            strayPixels(bitmap, behind.channel, backCard, inset)
        assertEquals(emptyList(), strays.take(10), "photo pixels past their card's outline in $name (${strays.size})")
    }

    /** Pixels of [channel]'s photo colour that sit closer than [inset] to [card]'s edge, or outside it. */
    private fun strayPixels(bitmap: Bitmap, channel: Int, card: Card, inset: Float): List<String> {
        val strays = mutableListOf<String>()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (dominance(bitmap.getPixel(x, y), channel) <= TINT) continue
            val edge = card.signedDistance(x + 0.5f, y + 0.5f)
            if (edge > -inset) strays += "($x,$y) ${"%.1f".format(edge)}px from edge"
        }
        return strays
    }

    /** How far [channel] stands above the other two channels of [pixel]. */
    private fun dominance(pixel: Int, channel: Int): Int {
        val rgb = intArrayOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
        return rgb[channel] - rgb.filterIndexed { i, _ -> i != channel }.max()
    }

    /** A card [width] by [height] px with corners of [radius], turned [tiltDegrees] about its centre. */
    private data class Card(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val tiltDegrees: Float,
    ) {
        /** Negative inside the card's rounded shape, positive outside, in px. */
        fun signedDistance(x: Float, y: Float): Float {
            val radians = Math.toRadians(-tiltDegrees.toDouble())
            val dx = x - centerX
            val dy = y - centerY
            val localX = (dx * cos(radians) - dy * sin(radians)).toFloat()
            val localY = (dx * sin(radians) + dy * cos(radians)).toFloat()
            val qx = abs(localX) - (width / 2f - radius)
            val qy = abs(localY) - (height / 2f - radius)
            return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - radius
        }
    }

    /** A photo filled edge to edge with one colour that [channel] stands out in. */
    private class Photo(val width: Int, val height: Int, val color: Int, val channel: Int) {
        fun bitmap(): Bitmap = Bitmap.createBitmap(width / 8, height / 8, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }
    }

    private companion object {
        const val RED = 0
        const val GREEN = 1
        val TALL = Photo(width = 1170, height = 2532, color = android.graphics.Color.rgb(255, 40, 40), channel = RED)
        val WIDE = Photo(width = 4032, height = 3024, color = android.graphics.Color.rgb(40, 220, 40), channel = GREEN)

        /** A channel this far above the other two is a photo's colour, faint fringe included. */
        const val TINT = 16
        const val OUTLINE_DP = 3f
    }
}
