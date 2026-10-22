package com.flipcash.app.messenger.internal.screens.boost

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.ReactionBoostPayer
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.payments.boost.BoostChat
import com.flipcash.shared.payments.boost.ReactionBoostSplit
import com.getcode.opencode.exchange.Exchange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** The keypad and the sheet read one resolver, so these cases cover both. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionBoostSplitResolverTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatId = ChatId(ByteArray(32) { 1 })
    private val viewer = List<Byte>(32) { 2 }
    private val other = List<Byte>(32) { 3 }
    private val creator = List<Byte>(32) { 9 }

    private fun resolver(type: ChatType, author: List<Byte>, host: List<Byte>?): ReactionBoostSplitResolver {
        val coordinator = mockk<ChatCoordinator> {
            coEvery { getMessage(chatId, 7) } returns mockk { every { senderId } returns author }
            every { observeMetadata(chatId) } returns flowOf(
                mockk<ChatMembership> {
                    every { metadata } returns mockk {
                        every { this@mockk.type } returns type
                        every { creator } returns host
                    }
                },
            )
        }
        val payer = mockk<ReactionBoostPayer> {
            every { plan(any(), any(), any()) } answers {
                ReactionBoostSplit.plan(viewer, firstArg(), secondArg<BoostChat>(), thirdArg())
            }
        }
        return ReactionBoostSplitResolver(coordinator, payer)
    }

    @Test
    fun `a DM pays the other member the whole`() = runTest {
        assertEquals(
            ReactionBoostViewModel.SplitCopy.Dm,
            resolver(ChatType.CONTACT_DM, other, null).resolve(chatId, 7),
        )
    }

    @Test
    fun `a group message from a member splits between author and host`() = runTest {
        assertEquals(
            ReactionBoostViewModel.SplitCopy.Shared,
            resolver(ChatType.GROUP, other, creator).resolve(chatId, 7),
        )
    }

    @Test
    fun `a group message from the host pays the host alone`() = runTest {
        assertEquals(
            ReactionBoostViewModel.SplitCopy.Whole,
            resolver(ChatType.GROUP, creator, creator).resolve(chatId, 7),
        )
    }

    @Test
    fun `the split stays unknown while the author is`() = runTest {
        val coordinator = mockk<ChatCoordinator> {
            coEvery { getMessage(chatId, 7) } returns null
            every { observeMetadata(chatId) } returns flowOf(null)
        }
        assertEquals(null, ReactionBoostSplitResolver(coordinator, mockk()).resolve(chatId, 7))
    }
}
