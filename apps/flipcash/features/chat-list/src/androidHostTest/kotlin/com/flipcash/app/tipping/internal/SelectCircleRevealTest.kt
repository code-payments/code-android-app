package com.flipcash.app.tipping.internal

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tipping.internal.components.AVATAR_TAG
import com.flipcash.app.tipping.internal.components.SELECT_CIRCLE_TAG
import com.flipcash.app.tipping.internal.components.TipChatRow
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Select mode's enter and exit: the row content slides right by the circle's slot while the
 * circle stays where it ends up and fades in, both driven by the one reveal progress.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class SelectCircleRevealTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun circleStaysPutAndFadesWhileContentSlides() {
        val progress = mutableFloatStateOf(0f)
        var background = Color.Unspecified
        var indicator = Color.Unspecified
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                background = CodeTheme.colors.background
                indicator = CodeTheme.colors.indicator
                TipChatRow(
                    chat = ConversationReference(
                        chatId = ChatId(byteArrayOf(1)),
                        displayName = "Grace Hopper",
                        handle = null,
                        lastMessagePreview = "Hello",
                        lastActivity = Instant.fromEpochMilliseconds(1_700_000_000_000),
                    ),
                    // Checked, so the circle is a filled disc whose colour shows its alpha.
                    circleReveal = { progress.floatValue },
                    circleChecked = true,
                    onClick = {},
                )
            }
        }

        val density = composeRule.density
        val tolerance = with(density) { 1.dp.toPx() }

        fun circleX() = composeRule.onNodeWithTag(SELECT_CIRCLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        fun avatarX() = composeRule.onNodeWithTag(AVATAR_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x

        composeRule.waitForIdle()
        val restingCircleX = circleX()
        val restingAvatarX = avatarX()

        // The circle plus its gap to the avatar: the distance the content slides at full progress.
        progress.floatValue = 1f
        composeRule.waitForIdle()
        val slot = avatarX() - restingAvatarX
        assertTrue(slot > with(density) { 20.dp.toPx() }, "content slid only $slot px")

        for (p in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            progress.floatValue = p
            composeRule.waitForIdle()

            assertEquals(restingCircleX, circleX(), tolerance, "circle moved at progress $p")
            assertEquals(slot * p, avatarX() - restingAvatarX, tolerance, "content offset at progress $p")

            // 3dp into the disc, level with its centre: inside the circle and left of the content
            // edge at every progress sampled here.
            val node = composeRule.onNodeWithTag(SELECT_CIRCLE_TAG, useUnmergedTree = true).fetchSemanticsNode()
            val x = (circleX() + with(density) { 3.dp.toPx() }).toInt()
            val y = (node.positionInRoot.y + node.size.height / 2f).toInt()
            val pixel = Color(composeRule.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y))
            // Alpha composites each sRGB channel linearly over the row background.
            fun mix(a: Float, b: Float) = a + (b - a) * p
            val expected = Color(
                mix(background.red, indicator.red),
                mix(background.green, indicator.green),
                mix(background.blue, indicator.blue),
            )
            assertTrue(
                abs(pixel.red - expected.red) < 0.03f && abs(pixel.green - expected.green) < 0.03f &&
                    abs(pixel.blue - expected.blue) < 0.03f,
                "circle colour at progress $p: ${Integer.toHexString(pixel.toArgb())}, " +
                    "expected ${Integer.toHexString(expected.toArgb())}",
            )
        }
    }
}
