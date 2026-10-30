package com.flipcash.app.messenger.internal.screens.boost

import com.flipcash.analytics.ChatType as AnalyticsChatType
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.ReactionBoostPayer
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.reactions.SelfReaction
import com.flipcash.shared.payments.boost.centsToUsd
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** `Sent Boost`: after the payment result, and only when a payment was attempted. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionBoostAnalyticsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(dispatcher)

    private val chatId = ChatId(ByteArray(1))
    private val analytics = RecordingAnalytics()
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val payer = mockk<ReactionBoostPayer>(relaxed = true)
    private val startChattingPayer = mockk<StartChattingPayer>(relaxed = true)

    @Before
    fun setUp() {
        every { startChattingPayer.observeSelectedToken() } returns flowOf(mockk(relaxed = true))
        every { chatCoordinator.observeChatReactions(any()) } returns
            flowOf(mapOf(7L to MessageReactions(emptyList(), listOf(SelfReaction("🔥", Instant.fromEpochSeconds(1))))))
        coEvery { chatCoordinator.getMessage(any(), any()) } returns mockk(relaxed = true) {
            every { senderId } returns listOf<Byte>(9)
            every { reactions } returns null
        }
        val metadata = mockk<ChatMembership>(relaxed = true) {
            every { metadata } returns mockk(relaxed = true) {
                every { type } returns ChatType.CONTACT_DM
                every { creator } returns null
            }
        }
        every { chatCoordinator.observeMetadata(any()) } returns flowOf(metadata)
    }

    private fun confirmedBoost(): ReactionBoostViewModel =
        ReactionBoostViewModel(
            chatCoordinator = chatCoordinator,
            payer = payer,
            splitResolver = mockk(relaxed = true),
            startChattingPayer = startChattingPayer,
            analytics = analytics,
            dispatchers = TestDispatcherProvider(dispatcher),
        ).also { vm ->
            vm.dispatchEvent(ReactionBoostViewModel.Event.Open(chatId, 7))
            vm.dispatchEvent(ReactionBoostViewModel.Event.SelectAmount(500))
            vm.dispatchEvent(ReactionBoostViewModel.Event.Confirm)
        }

    private val whole = centsToUsd(500).analytics

    @Test
    fun `a boost that is paid sends Sent Boost as a success with the whole amount`() {
        coEvery { payer.boost(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        confirmedBoost()

        assertEquals(
            listOf(TransferEvents.sentBoost(AnalyticsChatType.CONTACT, AnalyticsState.SUCCESS, whole, null)),
            analytics.events,
        )
    }

    @Test
    fun `a payment that fails sends Sent Boost as a failure with the error`() {
        coEvery { payer.boost(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("declined"))

        confirmedBoost()

        assertEquals(
            listOf(TransferEvents.sentBoost(AnalyticsChatType.CONTACT, AnalyticsState.FAILURE, whole, "declined")),
            analytics.events,
        )
    }

    @Test
    fun `a boost stopped before payment sends nothing`() {
        coEvery { payer.boost(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(StartChattingPayer.PaymentBlocked())

        confirmedBoost()

        assertEquals(emptyList(), analytics.events)
    }
}
