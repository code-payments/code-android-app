package com.flipcash.app.tipping

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Chats tab draws its list under the title bar and pads it by the bar's height, so rows scroll
 * beneath the title and fade into it rather than being cut at its edge. That is
 * [ScaffoldBarPlacement.Overlay], and both halves of it are checked here: the list's viewport spans
 * the whole screen (it is not inset by either bar), and the first row still rests below the top bar
 * — on the first frame, with no measurement pass to jump from.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ChatsTabLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listFillsScreenAndFirstRowClearsTheBar() {
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                CodeScaffold(
                    barPlacement = ScaffoldBarPlacement.Overlay,
                    topBar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("bar")) },
                    bottomBar = { Box(Modifier.fillMaxWidth().height(80.dp).testTag("bottom")) },
                ) { barPadding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("list"),
                        contentPadding = PaddingValues(top = barPadding.calculateTopPadding()),
                    ) {
                        items((0 until 40).toList()) {
                            Box(Modifier.fillMaxWidth().height(40.dp).testTag("row_$it"))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals("list starts at the top of the screen", 0f, top("list"), 0.5f)
        assertEquals(
            "list runs past the bottom bar",
            top("bottom") + height("bottom"),
            top("list") + height("list"),
            0.5f,
        )
        assertEquals(
            "first row rests below the top bar",
            top("bar") + height("bar"),
            top("row_0"),
            0.5f,
        )
    }

    private fun top(tag: String): Float =
        composeRule.onNode(hasTestTag(tag)).fetchSemanticsNode().positionInRoot.y

    private fun height(tag: String): Float =
        composeRule.onNode(hasTestTag(tag)).fetchSemanticsNode().size.height.toFloat()
}
