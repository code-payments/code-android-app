package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.session.TipCardEvent
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Covers the self-tip guard: scanning your own tip card has nothing to pay, so instead of
 * resolving a card it raises [TipCardEvent.OwnCardScanned] for the UI to act on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TipCardDelegateTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val self: ID = List(16) { it.toByte() }
    private val other: ID = List(16) { (it + 1).toByte() }

    private val tippingCoordinator = mockk<TippingCoordinator>(relaxed = true) {
        every { currentUserId } returns self
    }
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)

    private fun delegate() = TipCardDelegate(
        tippingCoordinator = tippingCoordinator,
        analytics = analytics,
        resources = resources,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    @Test
    fun `resolving your own tip card raises OwnCardScanned instead of resolving`() = runTest {
        val delegate = delegate()
        val event = async { delegate.tipCardEvents.first() }
        // Let the collector attach before emitting — the flow is replay-less.
        testScheduler.advanceUntilIdle()

        delegate.resolveTipCard(TipCardOwner.ById(self))

        assertEquals(TipCardEvent.OwnCardScanned, event.await())
        coVerify(exactly = 0) { tippingCoordinator.resolveTipCard(any<ID>()) }
    }

    @Test
    fun `resolving another user's tip card resolves and presents it`() = runTest {
        val card = mockk<Scannable.TipCard>(relaxed = true)
        coEvery { tippingCoordinator.resolveTipCard(other) } returns Result.success(card)

        val delegate = delegate()
        val presented = async { delegate.events.first() }
        testScheduler.advanceUntilIdle()

        delegate.resolveTipCard(TipCardOwner.ById(other))

        assertEquals(TipCardDelegate.Event.Present(card), presented.await())
    }
}
