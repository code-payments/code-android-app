package com.flipcash.app.messenger.internal.invites

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.messenger.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Approve New Members swaps the link card's pill and caption and the row's subtitle through a
 * crossfade; once it settles, only the new text is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class InviteHostSheetCrossfadeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun turningApproveOnSettlesOnTheNewText() {
        val approve = mutableStateOf(false)
        composeRule.setContent {
            FlipcashPreview {
                InviteHostSheetContent(
                    groupName = "Bad Boys",
                    state = InviteHostSheetViewModel.State(
                        approve = approve.value,
                        invite = InviteLinkState.Ready(
                            url = "https://flipcash.com/i/7Kq2xYbPz9#s=preview",
                            expiresAt = null,
                            useCount = 0,
                            maxUses = 0,
                        ),
                        plainUrl = "https://flipcash.com/chat/6f1c0b2e-3a4d-4c5e-8f90-123456789abc",
                    ),
                    onShare = {},
                    onApproveChanged = {},
                    onManageInvites = {},
                    onDismiss = {},
                )
            }
        }
        val off = string(R.string.subtitle_approveNewMembersOff)
        val on = string(R.string.subtitle_approveNewMembersOn)
        val asks = string(R.string.label_joinLinkAsksToJoin)

        composeRule.onNodeWithText(off).assertExists()
        composeRule.onNodeWithText(asks).assertDoesNotExist()

        approve.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText(off).assertDoesNotExist()
        composeRule.onNodeWithText(on).assertExists()
        composeRule.onNodeWithText(asks).assertExists()
    }
}
