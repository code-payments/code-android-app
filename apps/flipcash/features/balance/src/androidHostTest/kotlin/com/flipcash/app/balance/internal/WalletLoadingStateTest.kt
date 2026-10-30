package com.flipcash.app.balance.internal

import com.flipcash.shared.transactionhistory.FeedSyncState
import com.flipcash.shared.transactionhistory.TransactionAvatar
import com.flipcash.shared.transactionhistory.TransactionListItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The wallet tab must not mistake a cold local cache for an empty account: on a fresh login the
 * activity feed and the added-money flag both read empty until the first sync lands, and
 * rendering that verdict greets an established user with the Add Money row.
 */
class WalletLoadingStateTest {

    private val aTransaction = TransactionListItem(
        id = "1",
        messageId = listOf<Byte>(1),
        title = "Received",
        timestamp = Instant.fromEpochSeconds(0),
        avatar = TransactionAvatar.Generic(),
        signedAmountPrefix = "+",
        amount = null,
        fee = null,
        canCancel = false,
    )

    @Test
    fun `awaits activity before the milestones have reported`() {
        assertTrue(WalletViewModel.State().isAwaitingActivity)
    }

    @Test
    fun `still awaits activity when milestones report against an unsynced feed`() {
        val state = WalletViewModel.State(
            receivedMoney = false,
            feedSyncState = FeedSyncState.Unknown,
        )
        assertTrue(state.isAwaitingActivity)
    }

    @Test
    fun `stops awaiting once the feed has synced`() {
        val state = WalletViewModel.State(
            receivedMoney = false,
            feedSyncState = FeedSyncState.Synced,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `stops awaiting when the feed is unreachable rather than spinning forever`() {
        val state = WalletViewModel.State(
            receivedMoney = false,
            feedSyncState = FeedSyncState.Unavailable,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `cached rows short-circuit the wait — there is nothing to mistake for a new account`() {
        val state = WalletViewModel.State(
            receivedMoney = true,
            transactions = listOf(aTransaction),
            feedSyncState = FeedSyncState.Unknown,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `a held balance short-circuits the wait on an unsynced feed`() {
        val state = WalletViewModel.State(
            receivedMoney = true,
            transactions = emptyList(),
            feedSyncState = FeedSyncState.Unknown,
            holdsBalance = true,
        )
        assertFalse(state.isAwaitingActivity)
    }

    @Test
    fun `a held balance does not pre-empt the milestones themselves`() {
        val state = WalletViewModel.State(
            receivedMoney = null,
            feedSyncState = FeedSyncState.Synced,
            holdsBalance = true,
        )
        assertTrue(state.isAwaitingActivity)
    }

    @Test
    fun `add money row is hidden while nothing is known`() {
        assertFalse(WalletViewModel.State().showAddMoney)
    }

    @Test
    fun `add money row is hidden while history is unsynced with nothing stored`() {
        val state = WalletViewModel.State(receivedMoney = false, feedSyncState = FeedSyncState.Unknown)
        assertFalse(state.showAddMoney)
    }

    @Test
    fun `add money row shows once history synced and no money was added`() {
        val state = WalletViewModel.State(receivedMoney = false, feedSyncState = FeedSyncState.Synced)
        assertTrue(state.showAddMoney)
        assertFalse(state.hasReceivedMoney)
    }

    @Test
    fun `add money row is hidden once money was added, and the tiles show`() {
        val state = WalletViewModel.State(receivedMoney = true, feedSyncState = FeedSyncState.Synced)
        assertFalse(state.showAddMoney)
        assertTrue(state.hasReceivedMoney)
    }

    @Test
    fun `hasReceivedMoney is false while unknown, gating the action tiles`() {
        assertFalse(WalletViewModel.State().hasReceivedMoney)
    }

    /**
     * An account funded before this install has money but no local feed row to prove it. The
     * flag is fed `hasEverReceivedMoney || holdsBalance`, so the row hides and the action tiles
     * show off the balance alone.
     */
    @Test
    fun `a held balance counts as money added without a feed row`() {
        val state = WalletViewModel.State(
            receivedMoney = true,
            feedSyncState = FeedSyncState.Synced,
            holdsBalance = true,
        )
        assertTrue(state.hasReceivedMoney)
        assertFalse(state.showAddMoney)
    }
}
