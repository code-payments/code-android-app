package com.flipcash.app.messenger.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.link.LinkCardMemory
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Rate
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P22d: the transcript's first draw waits for the saved link previews, up to 300 ms, so a card is
 * drawn resolved rather than growing into it. Once they are loaded there is nothing to wait for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPreviewsReadyTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(StandardTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        coEvery { getPinnedMessages(any()) } returns Result.failure(IllegalStateException("no pins in this test"))
        every { observeMediaSendProgress() } returns emptyFlow()
    }
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val contactCoordinator = mockk<ContactCoordinator>(relaxed = true)
    private val featuredGroups = FeaturedGroupsStore(mockk(relaxed = true))

    init {
        every { exchange.preferredRate } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tipPaymentDelegate.startChattingFee(any()) } returns flowOf(null)
    }

    /** Loads [loadsAfter] after it is asked, or never. */
    private class SlowMemory(private val loadsAfter: Long?) : LinkCardMemory() {
        private val done = MutableStateFlow(false)
        override val isLoaded: Boolean get() = done.value

        override suspend fun awaitLoaded() {
            if (loadsAfter == null) kotlinx.coroutines.awaitCancellation()
            delay(loadsAfter)
            done.value = true
        }
    }

    private class LoadedMemory : LinkCardMemory()

    private fun createViewModel(
        memory: LinkCardMemory?,
    ): ChatViewModel = chatViewModel(
        dispatcher = mainCoroutineRule.dispatcher,
        chatCoordinator = chatCoordinator,
        contactCoordinator = contactCoordinator,
        tipPaymentDelegate = tipPaymentDelegate,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        featuredGroups = featuredGroups,
        linkCardMemory = memory,
    )

    @Test
    fun `a store that never loads holds the first draw for 300 ms and then lets it through`() = runTest {
        val viewModel = createViewModel(SlowMemory(loadsAfter = null))
        runCurrent()
        assertFalse(viewModel.previewsReady.value, "the draw should wait at first")

        advanceTimeBy(299)
        assertFalse(viewModel.previewsReady.value, "the draw should still be waiting at 299 ms")

        advanceTimeBy(1)
        runCurrent()
        assertTrue(viewModel.previewsReady.value, "the draw should go ahead at 300 ms with what it has")
    }

    @Test
    fun `a store that loads inside the wait lets the draw through when it does`() = runTest {
        val viewModel = createViewModel(SlowMemory(loadsAfter = 120))
        runCurrent()
        assertFalse(viewModel.previewsReady.value)

        advanceTimeBy(120)
        runCurrent()
        assertTrue(viewModel.previewsReady.value, "the draw should not wait out the full 300 ms")
    }

    @Test
    fun `a store already loaded does not hold the draw at all`() = runTest {
        val viewModel = createViewModel(LoadedMemory())

        assertTrue(viewModel.previewsReady.value, "no wait, not even a frame")
    }

    @Test
    fun `no store means nothing to wait for`() = runTest {
        assertTrue(createViewModel(null).previewsReady.value)
    }
}
