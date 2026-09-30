package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the share-profile widget bubble to a PNG for
 * comparison with the design (node 10588:1979). Not an assertion test — it writes to
 * `build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h340dp-xxhdpi")
class ShareProfileWidgetScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersOwnProfile() = render("share_profile_widget.png") {
        ShareProfileWidgetBubble(
            displayName = "Brad Burnham",
            username = "brad_burnham_2",
            profilePicture = null,
            userId = null,
            isFromSelf = false,
            position = BubblePosition.Solo,
            maxWidth = 290.dp,
            onShare = {},
        )
    }

    @Test
    fun rendersOtherUserResolved() = render("share_profile_widget_other.png") {
        PreviewShareProfileWidget(
            state = previewUserResolved(name = "Satoshi Nakamoto", handle = "@satoshi", blurHash = null),
            username = "satoshi",
        )
    }

    @Test
    fun rendersLookupFailed() = render("share_profile_widget_failed.png") {
        PreviewShareProfileWidget(state = LinkCard.User.State.NotFound, username = "satoshi")
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CodeTheme.colors.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    content()
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
