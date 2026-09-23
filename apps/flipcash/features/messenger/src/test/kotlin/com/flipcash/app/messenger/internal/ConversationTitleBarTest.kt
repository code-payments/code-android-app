package com.flipcash.app.messenger.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.services.models.chat.ChatType
import com.getcode.navigation.core.CodeNavigator
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The conversation bar's title opens the subject's profile. It is the whole title row, so it draws
 * no indication — an unbounded ripple centred on a row that wide washed across the bar on every
 * tap. Dropping a ripple is easy to do by dropping the click's semantics with it, which is what
 * these hold in place: the row still announces itself as a button and still acts on a tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ConversationTitleBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val group = ChatSubject.Group(
        chatId = ChatId(byteArrayOf(1)),
        groupTitle = "Bad Boys",
        picture = null,
        memberCount = 412L,
        rules = null,
        isMember = true,
    )

    private val navigator = mockk<CodeNavigator>(relaxed = true)

    private fun show(onAction: (ChatAction) -> Unit = {}) {
        composeTestRule.setContent {
            FlipcashPreview {
                ChatTopBar(
                    navigator = navigator,
                    state = ChatViewModel.State(chatType = ChatType.GROUP, subject = group),
                    onBarHeightChange = {},
                    chatActionHandler = onAction,
                    dispatch = {},
                )
            }
        }
    }

    @Test
    fun `the title still announces itself as a button`() {
        show()

        composeTestRule.onNodeWithText("Bad Boys").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
    }

    @Test
    fun `tapping the title opens the profile`() {
        val actions = mutableListOf<ChatAction>()
        show(onAction = { actions += it })

        composeTestRule.onNodeWithText("Bad Boys").assert(hasClickAction()).performClick()

        assertEquals(listOf<ChatAction>(ChatAction.ViewProfile), actions.toList())
    }

    /**
     * The back arrow is a 40dp circle and the avatar starts 15dp past it. A tap that lands in that
     * gap is aimed at back — nothing else is there — so it goes back rather than doing nothing, and
     * a thumb reaching for back has the whole strip left of the avatar to land in.
     */
    @Test
    fun `tapping between the back arrow and the avatar goes back`() {
        val actions = mutableListOf<ChatAction>()
        show(onAction = { actions += it })

        // The arrow spans 15..55dp and the avatar starts at 70dp; 13..53dp is the pair's height.
        composeTestRule.onRoot().performTouchInput { click(Offset(62.dp.toPx(), 33.dp.toPx())) }

        verify(exactly = 1) { navigator.pop() }
        assertEquals(emptyList(), actions.toList())
    }

    @Test
    fun `tapping the corner outside the back circle goes back`() {
        show()

        // Inside the arrow's square but outside its 40dp circle, which clips its own click.
        composeTestRule.onRoot().performTouchInput { click(Offset(17.dp.toPx(), 15.dp.toPx())) }

        verify(exactly = 1) { navigator.pop() }
    }

    /** The arrow's own click and the expanded target around it must not both fire. */
    @Test
    fun `tapping the back arrow goes back once`() {
        show()

        composeTestRule.onRoot().performTouchInput { click(Offset(35.dp.toPx(), 33.dp.toPx())) }

        verify(exactly = 1) { navigator.pop() }
    }
}
