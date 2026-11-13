package com.flipcash.app.messenger

import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.messenger.internal.screens.invitePeopleSheetMetadata
import com.getcode.navigation.NavMetadataKeys
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Invite People is a full-height sheet under Group Money (frame 1-07, node 13729:672). Its two
 * chat steps are wrap-content sheets by type, so the chat flow's entries override that under
 * Group Money only; the classic shell and every other sheet keep their type's presentation.
 */
class ChatFlowInvitePeopleSheetTest {

    private val invitePeopleSteps = listOf(ChatStep.InviteToGroup, ChatStep.ShareInviteLink())

    private fun metadataOf(shell: NavBarShell, step: NavKey): Map<String, Any> =
        chatEntries(
            openKeyboard = false,
            resultStateRegistry = mutableStateMapOf(),
            invitePeopleSheet = invitePeopleSheetMetadata(shell),
        )(step).metadata

    @Test
    fun `group money presents both invite people steps as full-height sheets`() {
        invitePeopleSteps.forEach { step ->
            val metadata = metadataOf(NavBarShell.GroupMoney, step)
            assertEquals(false, metadata[NavMetadataKeys.IsWrapContentSheet.key], "$step")
            assertEquals(true, metadata[NavMetadataKeys.IsSheet.key], "$step")
        }
    }

    @Test
    fun `classic keeps both invite people steps wrapping their content`() {
        invitePeopleSteps.forEach { step ->
            assertEquals(true, metadataOf(NavBarShell.Classic, step)[NavMetadataKeys.IsWrapContentSheet.key], "$step")
        }
    }

    @Test
    fun `group money leaves the chat flow's other wrap-content sheets alone`() {
        listOf(ChatStep.InviteHost, ChatStep.NewInviteLink, ChatStep.InviteLinkActions("00")).forEach { step ->
            assertEquals(true, metadataOf(NavBarShell.GroupMoney, step)[NavMetadataKeys.IsWrapContentSheet.key], "$step")
        }
    }
}
