package com.flipcash.app.session.internal.delegates

import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

class ChatCashLinkDelegateTest {

    private val funding = mockk<GiftCardFunding>(relaxed = true)
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val owner = mockk<AccountCluster>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)
    private val giftCard = mockk<GiftCardAccount>(relaxed = true)
    private val chatId = ChatId(UUID.randomUUID().bytes)
    private val localFiat = LocalFiat(Fiat(5.0), Fiat(5.0))
    private val amount = VerifiedFiat(localFiat, verifiedState = mockk<VerifiedState>(relaxed = true))

    private val delegate = ChatCashLinkDelegate(funding, chatCoordinator)

    @Before
    fun setUp() {
        mockkObject(GiftCardAccount.Companion)
        every { GiftCardAccount.create(any(), any()) } returns giftCard
        every { giftCard.mnemonic.getBase58EncodedEntropy() } returns ENTROPY
        coEvery { funding.fund(any(), any(), any(), any(), any()) } returns Result.success(localFiat)
        coEvery { chatCoordinator.sendMessage(any(), any(), any()) } returns Result.success(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkObject(GiftCardAccount.Companion)
    }

    @Test
    fun `funds the link, then posts only its URL`() = runTest {
        val result = delegate.sendToChat(chatId, amount, token, owner)

        assertTrue(result.isSuccess)
        coVerifyOrder {
            funding.fund(giftCard, owner, localFiat, token, any())
            chatCoordinator.sendMessage(chatId, "https://send.flipcash.com/c/#/e=$ENTROPY", null)
        }
        coVerify(exactly = 0) { funding.cancel(any(), any()) }
    }

    @Test
    fun `a failed fund posts nothing`() = runTest {
        coEvery { funding.fund(any(), any(), any(), any(), any()) } returns Result.failure(RuntimeException("rejected"))

        val result = delegate.sendToChat(chatId, amount, token, owner)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { chatCoordinator.sendMessage(any(), any(), any()) }
        coVerify(exactly = 0) { funding.cancel(any(), any()) }
    }

    @Test
    fun `a failed post cancels the link`() = runTest {
        coEvery { chatCoordinator.sendMessage(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))

        val result = delegate.sendToChat(chatId, amount, token, owner)

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { funding.cancel(owner, giftCard) }
    }

    @Test
    fun `an amount without a verified state neither funds nor posts`() = runTest {
        val result = delegate.sendToChat(chatId, VerifiedFiat(localFiat), token, owner)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { funding.fund(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { chatCoordinator.sendMessage(any(), any(), any()) }
    }

    private companion object {
        const val ENTROPY = "4sRnD8f3pk"
    }
}
