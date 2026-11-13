package com.flipcash.app.tipping.internal.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tipping.NavBarShellPrivacy
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.features.tipping.R
import com.getcode.theme.BadgeFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Group Money phase 2, item 2: New Group (frames 1-04 node 13729:462, 1-05 node 13729:552). One card,
 * "Group Name" placeholder, no footnote; Create grey while the name is blank and blue once it has one.
 * The classic shell keeps today's form.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class NewGroupFormGroupMoneyInviteSpec {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun show(shell: NavBarShell, state: CreateGroupViewModel.State, onCreate: () -> Unit = {}) {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashPreview(showBackground = true) {
                    NewGroupFormContent(
                        state = state,
                        onBack = {},
                        onCreate = onCreate,
                        onPickPicture = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val blank = CreateGroupViewModel.State(isPrivate = true, titleFieldState = TextFieldState())
    private val named = CreateGroupViewModel.State(isPrivate = true, titleFieldState = TextFieldState("Road Trip"))

    @Test
    fun `group money form reads New Group with a Group Name placeholder and no footnote`() {
        show(NavBarShell.GroupMoney, blank)

        composeRule.onNodeWithText("New Group").assertIsDisplayed()
        composeRule.onNodeWithText("Group Name").assertIsDisplayed()
        composeRule.onNodeWithText("Group Name (Required)").assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.subtitle_privateGroupFootnote)).assertDoesNotExist()
    }

    @Test
    fun `group money Create is disabled while the name is blank`() {
        show(NavBarShell.GroupMoney, blank)

        composeRule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `group money Create is enabled once a name is entered and creates on tap`() {
        var created = 0
        show(NavBarShell.GroupMoney, named, onCreate = { created++ })

        composeRule.onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals(1, created)
    }

    @Test
    fun `group money Create is blue when enabled`() {
        show(NavBarShell.GroupMoney, named)

        assertTrue(close(BadgeFocus, createFill()), "Create fill ${hex(createFill())}, wanted ${hex(BadgeFocus.toArgb())}")
    }

    @Test
    fun `group money Create is not blue while disabled`() {
        show(NavBarShell.GroupMoney, blank)

        assertFalse(close(BadgeFocus, createFill()), "disabled Create should stay grey, was ${hex(createFill())}")
    }

    @Test
    fun `group money makes every new group private, whatever the route asked for`() {
        assertTrue(NavBarShellPrivacy.newGroupIsPrivate(routeIsPrivate = false, shell = NavBarShell.GroupMoney))
        assertTrue(NavBarShellPrivacy.newGroupIsPrivate(routeIsPrivate = true, shell = NavBarShell.GroupMoney))
    }

    @Test
    fun `classic keeps the route's privacy`() {
        assertFalse(NavBarShellPrivacy.newGroupIsPrivate(routeIsPrivate = false, shell = NavBarShell.Classic))
        assertTrue(NavBarShellPrivacy.newGroupIsPrivate(routeIsPrivate = true, shell = NavBarShell.Classic))
    }

    @Test
    fun `classic private form keeps the required placeholder and the footnote`() {
        show(NavBarShell.Classic, blank)

        composeRule.onNodeWithText("New Group").assertIsDisplayed()
        composeRule.onNodeWithText("Group Name (Required)").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.subtitle_privateGroupFootnote)).assertIsDisplayed()
    }

    @Test
    fun `classic public form keeps its own title`() {
        show(NavBarShell.Classic, CreateGroupViewModel.State(isPrivate = false))

        composeRule.onNodeWithText(string(R.string.title_newPublicGroup)).assertIsDisplayed()
    }

    /** The pill's fill, read just inside its leading edge, clear of the label. */
    private fun createFill(): Int {
        val bounds = composeRule.onNodeWithText("Create").fetchSemanticsNode().boundsInRoot
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return bitmap.getPixel((bounds.left + 6f).roundToInt(), bounds.center.y.roundToInt())
    }

    private fun close(expected: Color, actual: Int): Boolean {
        val e = expected.toArgb()
        return listOf(16, 8, 0).all { shift -> abs((e shr shift and 0xFF) - (actual shr shift and 0xFF)) <= 8 }
    }

    private fun hex(argb: Int) = "#%08X".format(argb)
}
