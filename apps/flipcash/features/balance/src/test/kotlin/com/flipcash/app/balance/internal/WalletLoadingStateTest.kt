package com.flipcash.app.balance.internal

import com.flipcash.app.balance.internal.components.TutorialItem
import com.flipcash.shared.transactionhistory.FeedSyncState
import com.flipcash.shared.transactionhistory.TransactionAvatar
import com.flipcash.shared.transactionhistory.TransactionListItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The wallet tab must not mistake a cold local cache for an empty account: on a fresh login the
 * activity feed and the onboarding milestones both read empty until the first sync lands, and
 * rendering that verdict greets an established user with the new-user tutorial.
 */
class WalletLoadingStateTest {

    private fun milestones(addedMoney: Boolean, tipped: Boolean) = listOf(
        TutorialItem.AddMoney(isCompleted = addedMoney),
        TutorialItem.ScanTipCard(isCompleted = tipped),
    )

    private val aTransaction = TransactionListItem(
        id = "1",
        title = "Received",
        timestamp = Instant.fromEpochSeconds(0),
        avatar = TransactionAvatar.Generic,
        signedAmountPrefix = "+",
        amount = null,
        canCancel = false,
    )

    @Test
    fun `awaits activity before the milestones have reported`() {
        assertTrue(WalletViewModel.State().isAwaitingActivity)
    }

    @Test
    fun `still awaits activity when milestones report against an unsynced feed`() {
        val state = WalletViewModel.State(
            onboardingItems = milestones(addedMoney = false, tipped = false),
            feedSyncState = FeedSyncState.Unknown,
        )
        assertTrue(state.isAwaitingActivity)
    }

    @Test
    fun `stops awaiting once the feed has synced`() {
        val state = WalletViewModel.State(
            onboardingItems = milestones(addedMoney = false, tipped = false),
            feedSyncState = FeedSyncState.Synced,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `stops awaiting when the feed is unreachable rather than spinning forever`() {
        val state = WalletViewModel.State(
            onboardingItems = milestones(addedMoney = false, tipped = false),
            feedSyncState = FeedSyncState.Unavailable,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `cached rows short-circuit the wait — there is nothing to mistake for a new account`() {
        val state = WalletViewModel.State(
            onboardingItems = milestones(addedMoney = true, tipped = false),
            transactions = listOf(aTransaction),
            feedSyncState = FeedSyncState.Unknown,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `tutorial is withheld while the milestones are unknown`() {
        assertTrue(WalletViewModel.State().isNewUserTutorialComplete)
    }

    @Test
    fun `tutorial shows once a milestone is known to be outstanding`() {
        val state = WalletViewModel.State(
            onboardingItems = milestones(addedMoney = true, tipped = false),
            feedSyncState = FeedSyncState.Synced,
        )
        assertFalse(state.isNewUserTutorialComplete)
    }

    @Test
    fun `tutorial is complete when every milestone is`() {
        val state = WalletViewModel.State(
            onboardingItems = milestones(addedMoney = true, tipped = true),
            feedSyncState = FeedSyncState.Synced,
        )
        assertTrue(state.isNewUserTutorialComplete)
    }

    @Test
    fun `hasReceivedMoney is false while unknown, gating the action tiles`() {
        assertFalse(WalletViewModel.State().hasReceivedMoney)
    }
}
