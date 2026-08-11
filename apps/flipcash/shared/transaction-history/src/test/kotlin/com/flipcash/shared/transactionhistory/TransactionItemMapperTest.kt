package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.core.feed.MessageSubstitution
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.transactionhistory.internal.TransactionItemMapper
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
import com.getcode.utils.hexEncodedString
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionItemMapperTest {

    private val mapper = TransactionItemMapper()

    private val knownUserId: ID = listOf<Byte>(0x0A, 0x0B, 0x0C)
    private val knownProfile = UserProfile.Empty.copy(displayName = "Sally The Streamer")
    private val cached: Map<String, UserProfile> = mapOf(knownUserId.hexEncodedString() to knownProfile)

    private fun feedMessage(
        metadata: MessageMetadata?,
        amountUsd: Double = 20.0,
    ): ActivityFeedMessage = ActivityFeedMessage(
        id = listOf(0x01, 0x02, 0x03).map { it.toByte() },
        text = "Tipped Sally The Streamer",
        amount = LocalFiat(
            usdf = Fiat(amountUsd, CurrencyCode.USD),
            nativeAmount = Fiat(amountUsd, CurrencyCode.USD),
        ),
        timestamp = Instant.fromEpochSeconds(1700000000L),
        state = MessageState.COMPLETED,
        metadata = metadata,
    )

    private fun usdfToken(): Token = token(address = Mint.usdf, name = "USDF", symbol = "USDF")

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
    fun `send to a cached user resolves a profile avatar and a minus prefix`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("-", item.signedAmountPrefix)
        assertEquals(TransactionAvatar.Profile(knownProfile), item.avatar)
    }

    @Test
    fun `receive from a cached user resolves a profile avatar and a plus prefix`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("+", item.signedAmountPrefix)
        assertEquals(TransactionAvatar.Profile(knownProfile), item.avatar)
    }

    @Test
    fun `unresolved counterparty falls back to a generic avatar`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals(TransactionAvatar.Generic, item.avatar)
        assertEquals("-", item.signedAmountPrefix)
    }

    @Test
    fun `bare verb title appends the resolved counterparty name`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
            .copy(text = "Tipped", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Tipped Sally The Streamer", item.title)
    }

    @Test
    fun `bare verb title stays bare until the counterparty profile resolves`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
            .copy(text = "Tipped", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals("Tipped", item.title)
    }

    @Test
    fun `received tip reads Received Tip From the counterparty`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
            .copy(text = "Received", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Received Tip From Sally The Streamer", item.title)
    }

    @Test
    fun `bought token appends the token name`() {
        val token = token(address = Mint.usdc, name = "Dad Cash", symbol = "DADCASH")
        val msg = feedMessage(metadata = MessageMetadata.BoughtToken)
            .copy(text = "Purchased", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("Purchased Dad Cash", item.title)
    }

    @Test
    fun `bought dollars reads Added Money instead of Purchased Dollars`() {
        val msg = feedMessage(metadata = MessageMetadata.BoughtToken)
            .copy(text = "Purchased", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, usdfToken()) to emptyMap())

        assertEquals("Added Money", item.title)
    }

    @Test
    fun `sold token appends the token name`() {
        val token = token(address = Mint.usdc, name = "Dad Cash", symbol = "DADCASH")
        val msg = feedMessage(metadata = MessageMetadata.SoldToken)
            .copy(text = "Sold", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("Sold Dad Cash", item.title)
    }

    @Test
    fun `bought token stays bare until token metadata resolves`() {
        val msg = feedMessage(metadata = MessageMetadata.BoughtToken)
            .copy(text = "Purchased", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals("Purchased", item.title)
    }

    @Test
    fun `title placeholder resolves to the observed name over the fallback`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
            .copy(
                text = "Tipped {0}",
                textSubstitutions = listOf(
                    MessageSubstitution.UserId(fallback = "Stale Name", userId = knownUserId),
                ),
            )
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Tipped Sally The Streamer", item.title)
    }

    @Test
    fun `title placeholder falls back to the server name when unresolved`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
            .copy(
                text = "Tipped {0}",
                textSubstitutions = listOf(
                    MessageSubstitution.UserId(fallback = "Server Sally", userId = knownUserId),
                ),
            )
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals("Tipped Server Sally", item.title)
    }

    @Test
    fun `deposit uses the token icon and a plus prefix`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.DepositedCrypto, amountUsd = 30.0)
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("+", item.signedAmountPrefix)
        assertEquals(TransactionAvatar.TokenIcon(token), item.avatar)
    }

    @Test
    fun `withdraw uses the token icon and a minus prefix`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.WithdrewCrypto)
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("-", item.signedAmountPrefix)
        assertEquals(TransactionAvatar.TokenIcon(token), item.avatar)
    }

    @Test
    fun `null metadata yields null prefix and a generic avatar`() {
        val msg = feedMessage(metadata = null)
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals(null, item.signedAmountPrefix)
        assertEquals(TransactionAvatar.Generic, item.avatar)
    }

    @Test
    fun `canCancel is true for indirectly sent with the canCancel flag`() {
        val creator = PublicKey(ByteArray(32).toList())
        val msg = feedMessage(metadata = MessageMetadata.IndirectlySentCrypto(creator, canCancel = true))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals(true, item.canCancel)
        assertEquals("-", item.signedAmountPrefix)
    }
}
