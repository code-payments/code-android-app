package com.flipcash.app.messenger.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.services.models.chat.ChatType
import com.getcode.navigation.core.CodeNavigator
import io.mockk.mockk
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

    private fun show(onAction: (ChatAction) -> Unit = {}) {
        composeTestRule.setContent {
            FlipcashPreview {
                ChatTopBar(
                    navigator = mockk<CodeNavigator>(relaxed = true),
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
}
