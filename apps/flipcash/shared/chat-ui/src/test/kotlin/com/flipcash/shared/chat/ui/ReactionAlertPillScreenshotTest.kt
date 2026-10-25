package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.alerts.ReactionAlertContent
import com.flipcash.shared.chat.alerts.ReactionAlertContent.Kind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders each pill state of Figma `Alert/Reaction pill` (node 12643:117) to `build/screenshots/`.
 * Not an assertion test: it shows layout and copy for comparison with the design. Animation is off
 * so each file is the settled state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h170dp-xhdpi")
class ReactionAlertPillScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chat = ChatId(byteArrayOf(1))
    private val quote = "Me! I'll send my \$40 tonight"

    private fun content(kind: Kind, actors: List<String?> = listOf("Chloe"), emojis: List<String> = listOf("🔥"), boost: Long? = null) =
        ReactionAlertContent(chat, 1, kind, "Ballers", quote, actors, emojis, boost)

    @Test fun rest() = render(content(Kind.Reaction), ReactionAlertPhase.Rest, "alert_rest.png")

    @Test fun compactReaction() = render(content(Kind.Reaction), ReactionAlertPhase.Compact, "alert_compact_reaction.png")

    @Test fun compactBoost() = render(content(Kind.Boost, boost = 500), ReactionAlertPhase.Compact, "alert_compact_boost.png")

    @Test fun expandedReaction() = render(content(Kind.Reaction), ReactionAlertPhase.Expanded, "alert_expanded_reaction.png")

    @Test fun expandedBoost() = render(content(Kind.Boost, boost = 500), ReactionAlertPhase.Expanded, "alert_expanded_boost.png")

    @Test fun expandedStacked() = render(
        content(Kind.Several, actors = listOf("Chloe", "Sam", "Jo"), emojis = listOf("🔥", "❤️", "😂")),
        ReactionAlertPhase.Expanded,
        "alert_expanded_stacked.png",
    )

    @Test fun expandedInActiveChat() =
        render(content(Kind.Reaction), ReactionAlertPhase.Expanded, "alert_expanded_this_chat.png", inActiveChat = true)

    private fun render(content: ReactionAlertContent, phase: ReactionAlertPhase, fileName: String, inActiveChat: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Frame {
                ReactionAlertPill(content, phase, inActiveChat, animate = false)
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("build/screenshots").apply { mkdirs() }.resolve(fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath}")
    }

    // A dark-grey stage so the black pill reads against it.
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        FlipcashPreview(showBackground = false) {
            Box(Modifier.background(Color(0xFF2A2A2A)).padding(12.dp), contentAlignment = Alignment.TopCenter) {
                content()
            }
        }
    }
}
