package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.core.feed.SwapState
import com.flipcash.app.core.feed.SwappedCryptoMetadata
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.transactionhistory.internal.TransactionDetailsMapper
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.FakeResourceHelper
import com.getcode.utils.base58
import com.getcode.utils.hexEncodedString
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The details mapper's readings of the metadata — which kind, which status, which actions.
 *
 * Deliberately mirrors [TransactionItemMapperTest]'s fixtures: the two mappers read the same entry,
 * and a row and the screen it opens disagreeing about what the entry was is the failure worth
 * catching.
 */
class TransactionDetailsMapperTest {

    private val resources = FakeResourceHelper()
        .stub(R.string.title_activity_convert, "%1\$s → %2\$s")
        .stub(R.string.label_txnDetails_inPerson, "In Person")
    private val mapper = TransactionDetailsMapper(resources)

    private val knownUserId: ID = listOf<Byte>(0x0A, 0x0B, 0x0C)
    private val knownProfile = UserProfile.Empty.copy(displayName = "Sally The Streamer")
    private val cached: Map<String, UserProfile> = mapOf(knownUserId.hexEncodedString() to knownProfile)

    private val vault = PublicKey.fromBase58("11111111111111111111111111111111")

    private fun feedMessage(
        metadata: MessageMetadata?,
        text: String = "Sent",
        state: MessageState = MessageState.COMPLETED,
    ): ActivityFeedMessage = ActivityFeedMessage(
        id = listOf(0x01, 0x02, 0x03).map { it.toByte() },
        text = text,
        amount = LocalFiat(
            usdf = Fiat(20.0, CurrencyCode.USD),
            nativeAmount = Fiat(20.0, CurrencyCode.USD),
        ),
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = state,
        metadata = metadata,
    )

    private fun map(
        metadata: MessageMetadata?,
        text: String = "Sent",
        state: MessageState = MessageState.COMPLETED,
        token: Token? = null,
        toToken: Token? = null,
    ): TransactionDetails = mapper.map(
        ActivityFeedMessageWithToken(
            feedMessage(metadata, text, state),
            token = token,
            toToken = toToken,
        ) to cached
    )

    private fun token(address: Mint, name: String, symbol: String): Token = MintMetadata(
        address = address,
        decimals = 6,
        name = name,
        symbol = symbol,
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = PublicKey.fromBase58("11111111111111111111111111111111"),
            authority = PublicKey.fromBase58("11111111111111111111111111111111"),
            lockDurationInDays = 21,
        ),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    @Test
    fun `the verb separates a tip from a plain send`() {
        val meta = MessageMetadata.DirectlySentCrypto(userId = knownUserId)

        assertEquals(TransactionKind.Tipped, map(meta, text = "Tipped").kind)
        assertEquals(TransactionKind.Sent, map(meta, text = "Sent").kind)
    }

    @Test
    fun `a send with nobody named is a bill handed over, not a send`() {
        val details = map(MessageMetadata.DirectlySentCrypto())

        assertEquals(TransactionKind.GaveCash, details.kind)
        // Nobody to head the screen with, so the kind's own heading stands.
        assertNull(details.heading)
        assertEquals("In Person", details.subtitle)
    }

    @Test
    fun `a receive with nobody named is cash taken in person`() {
        assertEquals(TransactionKind.ReceivedCash, map(MessageMetadata.ReceivedCrypto()).kind)
    }

    @Test
    fun `a cached counterparty heads the screen and opens the conversation`() {
        val details = map(MessageMetadata.ReceivedCrypto(userId = knownUserId))

        assertEquals(TransactionKind.Received, details.kind)
        assertEquals("Sally The Streamer", details.heading)
        assertEquals(TransactionAvatar.Profile(knownProfile), details.avatar)
        assertTrue(details.canViewInChat)
        assertEquals("+", details.signedAmountPrefix)
    }

    @Test
    fun `an unresolved counterparty leaves the heading and the chat action to the kind`() {
        val details = mapper.map(
            ActivityFeedMessageWithToken(
                feedMessage(MessageMetadata.ReceivedCrypto(userId = knownUserId)),
                token = null,
            ) to emptyMap()
        )

        assertNull(details.heading)
        assertFalse(details.canViewInChat)
    }

    @Test
    fun `only an open cash link can be cancelled`() {
        val open = MessageMetadata.IndirectlySentCrypto(creator = vault, canCancel = true)
        val claimed = MessageMetadata.IndirectlySentCrypto(creator = vault, canCancel = false)

        assertEquals(TransactionKind.SentCashLink, map(open).kind)
        assertTrue(map(open).canCancel)
        assertFalse(map(claimed).canCancel)
        assertFalse(map(MessageMetadata.DirectlySentCrypto(userId = knownUserId)).canCancel)
    }

    @Test
    fun `a convert names both mints and draws both sides`() {
        val from = token(Mint.usdf, name = "Dollars", symbol = "USDF")
        val to = token(Mint(PublicKey.fromBase58("So11111111111111111111111111111111111111112").bytes), name = "Jeffy", symbol = "JEFFY")
        val meta = MessageMetadata.SwappedCrypto(
            SwappedCryptoMetadata(
                from = LocalFiat(usdf = Fiat(20.0, CurrencyCode.USD), nativeAmount = Fiat(20.0, CurrencyCode.USD)),
                toMint = to.address,
                toAmount = LocalFiat(usdf = Fiat(19.0, CurrencyCode.USD), nativeAmount = Fiat(19.0, CurrencyCode.USD)),
                fee = Fiat(1.0, CurrencyCode.USD),
                swapState = SwapState.SUCCEEDED,
            )
        )

        val details = map(meta, token = from, toToken = to)

        assertEquals(TransactionKind.Convert, details.kind)
        assertEquals("Dollars → Jeffy", details.subtitle)
        assertEquals(TransactionAvatar.SwapTokens(from = from, to = to), details.avatar)
        assertEquals(Fiat(1.0, CurrencyCode.USD), details.fee)
        assertEquals(Fiat(19.0, CurrencyCode.USD), details.received)
        // A swap debits the source mint.
        assertEquals("-", details.signedAmountPrefix)
    }

    @Test
    fun `a failed swap fails the entry, whatever the notification says`() {
        val to = token(Mint(PublicKey.fromBase58("So11111111111111111111111111111111111111112").bytes), name = "Jeffy", symbol = "JEFFY")
        val meta = MessageMetadata.SwappedCrypto(
            SwappedCryptoMetadata(
                from = LocalFiat(usdf = Fiat(20.0, CurrencyCode.USD), nativeAmount = Fiat(20.0, CurrencyCode.USD)),
                toMint = to.address,
                toAmount = null,
                fee = Fiat(0.0, CurrencyCode.USD),
                swapState = SwapState.FAILED,
            )
        )

        // The entry itself completes as soon as the source side is debited.
        val details = map(meta, state = MessageState.COMPLETED)

        assertEquals(TransactionStatus.Failed, details.status)
        assertNull(details.received)
    }

    @Test
    fun `a pending entry reads as pending`() {
        val details = map(MessageMetadata.DepositedCrypto, state = MessageState.PENDING)

        assertEquals(TransactionKind.Deposit, details.kind)
        assertEquals(TransactionStatus.Pending, details.status)
    }

    @Test
    fun `the copied id is base58, not the row's paging key`() {
        val msg = feedMessage(MessageMetadata.DepositedCrypto)
        val details = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals(msg.id.base58, details.id)
    }

    @Test
    fun `a withdrawal has no account to show`() {
        // Neither the message metadata nor the notification carries a destination address, so the
        // To/From row has nothing to render until one does.
        assertNull(map(MessageMetadata.WithdrewCrypto()).account)
    }
}
