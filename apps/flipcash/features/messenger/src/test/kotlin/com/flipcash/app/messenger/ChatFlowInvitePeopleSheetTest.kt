package com.flipcash.app.messenger

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.getcode.navigation.NavMetadataKeys
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Invite People wraps its content in both shells, as the sheet did before Group Money: its two chat
 * steps are wrap-content sheets by type, and the chat flow's entries keep that under Group Money.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatFlowInvitePeopleSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val invitePeopleSteps = listOf(
        ChatStep.InviteToGroup(),
        ChatStep.ShareInviteLink(),
        ChatStep.InviteToGroup(classic = true),
        ChatStep.ShareInviteLink(classic = true),
    )

    /** The chat flow's entries as the flow builds them with [shell] on. */
    private fun entriesUnder(shell: NavBarShell): (NavKey) -> NavEntry<NavKey> {
        lateinit var entries: (NavKey) -> NavEntry<NavKey>
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                entries = chatEntryProvider(
                    openKeyboard = false,
                    resultStateRegistry = remember { mutableStateMapOf() },
                )
            }
        }
        composeRule.waitForIdle()
        return entries
    }

    @Test
    fun `group money presents both invite people steps as wrap-content sheets`() {
        val entries = entriesUnder(NavBarShell.GroupMoney)
        invitePeopleSteps.forEach { step ->
            val metadata = entries(step).metadata
            assertEquals(true, metadata[NavMetadataKeys.IsWrapContentSheet.key], "$step")
            assertEquals(true, metadata[NavMetadataKeys.IsSheet.key], "$step")
        }
    }

    @Test
    fun `classic keeps both invite people steps wrapping their content`() {
        val entries = entriesUnder(NavBarShell.Classic)
        invitePeopleSteps.forEach { step ->
            assertEquals(true, entries(step).metadata[NavMetadataKeys.IsWrapContentSheet.key], "$step")
        }
    }

    @Test
    fun `group money leaves the chat flow's other wrap-content sheets alone`() {
        val entries = entriesUnder(NavBarShell.GroupMoney)
        listOf(ChatStep.InviteHost, ChatStep.NewInviteLink, ChatStep.InviteLinkActions("00")).forEach { step ->
            assertEquals(true, entries(step).metadata[NavMetadataKeys.IsWrapContentSheet.key], "$step")
        }
    }
}
