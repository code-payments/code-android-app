package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.core.feed.MessageSubstitution
import com.flipcash.app.core.feed.SwapState
import com.flipcash.app.core.feed.SwappedCryptoMetadata
import com.flipcash.services.models.BoostedReaction
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.shared.transactionhistory.internal.TransactionItemMapper
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.FakeResourceHelper
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

    private val resources = FakeResourceHelper()
        .stub(R.string.title_activity_tipFrom, "Tip from %1\$s")
        .stub(R.string.title_activity_sentTo, "Sent to %1\$s")
        .stub(R.string.title_activity_receivedFrom, "Received from %1\$s")
        .stub(R.string.title_activity_convert, "%1\$s → %2\$s")
        .stub(R.string.title_activity_boostedFor, "Boosted %1\$s for %2\$s")
        .stub(R.string.title_activity_boostFrom, "Boost %1\$s from %2\$s")
        .stub(R.string.title_activity_boostedIn, "Boosted %1\$s in %2\$s")
        .stub(R.string.title_activity_boosted, "Boosted %1\$s")
        .stub(R.string.title_activity_boostReceived, "Boost %1\$s received")
    private val mapper = TransactionItemMapper(resources)

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

        assertEquals(TransactionAvatar.Generic(), item.avatar)
        assertEquals("-", item.signedAmountPrefix)
    }

    @Test
    fun `sent tip appends the resolved counterparty name`() {
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
    fun `received tip reads Tip from the counterparty`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
            .copy(text = "Tipped", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Tip from Sally The Streamer", item.title)
    }

    @Test
    fun `an in-chat send reads Sent to the counterparty, not Tipped`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
            .copy(text = "Sent", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Sent to Sally The Streamer", item.title)
    }

    @Test
    fun `a received in-chat send reads Received from the counterparty, not Tip from`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
            .copy(text = "Sent", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Received from Sally The Streamer", item.title)
    }

    @Test
    fun `a received send keeps Received from when the server says Received`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
            .copy(text = "Received", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals("Received from Sally The Streamer", item.title)
    }

    @Test
    fun `a received send stays bare until the counterparty profile resolves`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
            .copy(text = "Sent", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals("Sent", item.title)
    }

    @Test
    fun `bought token renders the server text verbatim`() {
        val token = token(address = Mint.usdc, name = "Dad Cash", symbol = "DADCASH")
        val msg = feedMessage(metadata = MessageMetadata.BoughtToken)
            .copy(text = "Purchased", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("Purchased", item.title)
    }

    @Test
    fun `bought dollars renders the server text verbatim`() {
        val msg = feedMessage(metadata = MessageMetadata.BoughtToken)
            .copy(text = "Added", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, usdfToken()) to emptyMap())

        assertEquals("Added", item.title)
    }

    @Test
    fun `sold token renders the server text verbatim`() {
        val token = token(address = Mint.usdc, name = "Dad Cash", symbol = "DADCASH")
        val msg = feedMessage(metadata = MessageMetadata.SoldToken)
            .copy(text = "Sold", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("Sold", item.title)
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
    fun `sent tip badges the profile avatar with the token it moved in`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to cached)

        assertEquals(TransactionAvatar.Profile(knownProfile, badgeToken = token), item.avatar)
    }

    @Test
    fun `received tip badges the profile avatar with the token it moved in`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to cached)

        assertEquals(TransactionAvatar.Profile(knownProfile, badgeToken = token), item.avatar)
    }

    /** Token metadata arrives reactively; until it does the row draws the bare avatar. */
    @Test
    fun `profile avatar carries no badge until the token resolves`() {
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals(null, item.avatar.badgeToken)
    }

    /** A token-shaped avatar already names the token, so a badge would only repeat it. */
    @Test
    fun `token and swap avatars carry no badge`() {
        val token = usdfToken()
        val deposit = mapper.map(
            ActivityFeedMessageWithToken(
                feedMessage(metadata = MessageMetadata.DepositedCrypto),
                token,
            ) to emptyMap()
        )

        assertEquals(null, deposit.avatar.badgeToken)
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
        val msg = feedMessage(metadata = MessageMetadata.WithdrewCrypto())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals("-", item.signedAmountPrefix)
        assertEquals(TransactionAvatar.TokenIcon(token), item.avatar)
    }

    @Test
    fun `give with no identifier uses the token icon`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto())
            .copy(text = "Gave", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to cached)

        assertEquals(TransactionAvatar.TokenIcon(token), item.avatar)
        assertEquals("-", item.signedAmountPrefix)
        assertEquals("Gave", item.title)
    }

    @Test
    fun `grab with no identifier uses the token icon`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.ReceivedCrypto())
            .copy(text = "Received", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to cached)

        assertEquals(TransactionAvatar.TokenIcon(token), item.avatar)
        assertEquals("+", item.signedAmountPrefix)
    }

    /**
     * A named counterparty is still coming, so the avatar stays a silhouette rather than becoming
     * the token's icon — but the badge is already the token's, so the row gains only the face when
     * the profile lands.
     */
    @Test
    fun `send to a named but unresolved user keeps the generic avatar, badged with the token`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(userId = knownUserId))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals(TransactionAvatar.Generic(badgeToken = token), item.avatar)
    }

    @Test
    fun `send to a phone-only recipient keeps the generic avatar`() {
        val token = usdfToken()
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto(phoneNumber = "+15555550123"))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to emptyMap())

        assertEquals(TransactionAvatar.Generic(badgeToken = token), item.avatar)
    }

    /** No token has resolved from the mint cache yet, so there is no icon to draw. */
    @Test
    fun `give with no identifier and no token stays generic`() {
        val msg = feedMessage(metadata = MessageMetadata.DirectlySentCrypto())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to cached)

        assertEquals(TransactionAvatar.Generic(), item.avatar)
    }

    @Test
    fun `cash link uses the token icon`() {
        val token = usdfToken()
        val creator = PublicKey(ByteArray(32).toList())
        val msg = feedMessage(metadata = MessageMetadata.IndirectlySentCrypto(creator, canCancel = true))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token) to cached)

        assertEquals(TransactionAvatar.TokenIcon(token), item.avatar)
        assertEquals("-", item.signedAmountPrefix)
    }

    @Test
    fun `null metadata yields null prefix and a generic avatar`() {
        val msg = feedMessage(metadata = null)
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals(null, item.signedAmountPrefix)
        assertEquals(TransactionAvatar.Generic(), item.avatar)
    }

    @Test
    fun `canCancel is true for indirectly sent with the canCancel flag`() {
        val creator = PublicKey(ByteArray(32).toList())
        val msg = feedMessage(metadata = MessageMetadata.IndirectlySentCrypto(creator, canCancel = true))
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = null) to emptyMap())

        assertEquals(true, item.canCancel)
        assertEquals("-", item.signedAmountPrefix)
    }

    // -- converts (two mints, one row) --

    private fun convert(fee: Double = 0.25) = MessageMetadata.SwappedCrypto(
        swap = SwappedCryptoMetadata(
            from = LocalFiat(
                usdf = Fiat(20.0, CurrencyCode.USD),
                nativeAmount = Fiat(20.0, CurrencyCode.USD),
            ),
            toMint = Mint.usdc,
            toAmount = LocalFiat(
                usdf = Fiat(19.75, CurrencyCode.USD),
                nativeAmount = Fiat(19.75, CurrencyCode.USD),
                mint = Mint.usdc,
            ),
            fee = Fiat(fee, CurrencyCode.USD),
            swapState = SwapState.SUCCEEDED,
        ),
    )

    @Test
    fun `a convert shows both token logos and carries the fee`() {
        val from = usdfToken()
        val to = token(address = Mint.usdc, name = "Dad Cash", symbol = "DADCASH")
        val msg = feedMessage(metadata = convert()).copy(text = "Converted", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, from, to) to emptyMap())

        assertEquals(TransactionAvatar.SwapTokens(from, to), item.avatar)
        assertEquals(Fiat(0.25, CurrencyCode.USD), item.fee)
        assertEquals("-", item.signedAmountPrefix)
    }

    @Test
    fun `a convert titles itself with both token names instead of the server verb`() {
        val from = usdfToken()
        val to = token(address = Mint.usdc, name = "Dad Cash", symbol = "DADCASH")
        val msg = feedMessage(metadata = convert()).copy(text = "Converted", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, from, to) to emptyMap())

        assertEquals("USDF → Dad Cash", item.title)
    }

    @Test
    fun `a convert keeps the server title until both tokens resolve`() {
        val from = usdfToken()
        val msg = feedMessage(metadata = convert()).copy(text = "Converted", textSubstitutions = emptyList())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, from, toToken = null) to emptyMap())

        assertEquals("Converted", item.title)
    }

    @Test
    fun `a convert keeps the dual avatar while a token is still unresolved`() {
        val from = usdfToken()
        val msg = feedMessage(metadata = convert())
        val item = mapper.map(ActivityFeedMessageWithToken(msg, from, toToken = null) to emptyMap())

        assertEquals(TransactionAvatar.SwapTokens(from, null), item.avatar)
    }

    @Test
    fun `a non-convert carries no fee`() {
        val msg = feedMessage(metadata = MessageMetadata.DepositedCrypto)
        val item = mapper.map(ActivityFeedMessageWithToken(msg, usdfToken()) to emptyMap())

        assertEquals(null, item.fee)
    }

    // --- Boosted reaction titles (cases 1-8 of the spec) ---

    private fun boost(emoji: String) = BoostedReaction(
        chatId = ChatId(ByteArray(32) { 9 }),
        messageId = 7L,
        emoji = Emoji(emoji),
    )

    private fun title(
        metadata: MessageMetadata?,
        serverTitle: String,
        profiles: Map<String, UserProfile> = cached,
        chatName: String? = null,
        substitutions: List<MessageSubstitution> = emptyList(),
    ): String {
        val msg = feedMessage(metadata = metadata)
            .copy(text = serverTitle, textSubstitutions = substitutions)
        return mapper.map(
            ActivityFeedMessageWithToken(msg, token = null, boostChatName = chatName) to profiles
        ).title
    }

    private val fire = "\uD83D\uDD25"
    private val thumbs = "\uD83D\uDC4D\uD83C\uDFFD"

    @Test
    fun `1 sent boost names the payee`() {
        val meta = MessageMetadata.DirectlySentCrypto(userId = knownUserId, boostedReaction = boost(fire))
        assertEquals("Boosted $fire for Sally The Streamer", title(meta, "Sent to Sally The Streamer"))
    }

    @Test
    fun `2 received boost names the payer`() {
        val meta = MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost(fire))
        assertEquals("Boost $fire from Sally The Streamer", title(meta, "Received from Sally The Streamer"))
    }

    @Test
    fun `3 split boost is named for its chat`() {
        val meta = MessageMetadata.DirectlySentCrypto(boostedReaction = boost(fire))
        assertEquals(
            "Boosted $fire in Design Team",
            title(meta, "Sent", profiles = emptyMap(), chatName = "Design Team"),
        )
    }

    @Test
    fun `4 split boost with no chat in the store is just the emoji`() {
        val meta = MessageMetadata.DirectlySentCrypto(boostedReaction = boost(fire))
        assertEquals("Boosted $fire", title(meta, "Sent", profiles = emptyMap(), chatName = null))
    }

    @Test
    fun `5 received boost with an unresolved name reads as received`() {
        val meta = MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost(fire))
        assertEquals("Boost $fire received", title(meta, "Received", profiles = emptyMap()))
    }

    @Test
    fun `6 a boost wins over the tip verb`() {
        val meta = MessageMetadata.DirectlySentCrypto(userId = knownUserId, boostedReaction = boost(thumbs))
        assertEquals("Boosted $thumbs for Sally The Streamer", title(meta, "Tipped Sally The Streamer"))
    }

    @Test
    fun `7 a tip without a boost is unchanged`() {
        val meta = MessageMetadata.DirectlySentCrypto(userId = knownUserId)
        assertEquals("Tipped Sally The Streamer", title(meta, "Tipped"))
    }

    @Test
    fun `8 a receive without a boost is unchanged`() {
        val meta = MessageMetadata.ReceivedCrypto(userId = knownUserId)
        assertEquals("Received from Sally The Streamer", title(meta, "Received"))
    }

    @Test
    fun `a boost never shows the server title while the name is unresolved`() {
        val meta = MessageMetadata.DirectlySentCrypto(userId = knownUserId, boostedReaction = boost(fire))
        assertEquals("Boosted $fire", title(meta, "Sent to Sally", profiles = emptyMap()))
    }

    @Test
    fun `a boost wins over server substitutions`() {
        val meta = MessageMetadata.ReceivedCrypto(userId = knownUserId, boostedReaction = boost(fire))
        val subs = listOf(MessageSubstitution.UserId(fallback = "Sally", userId = knownUserId))
        assertEquals(
            "Boost $fire from Sally The Streamer",
            title(meta, "{0} sent you", substitutions = subs),
        )
    }

    @Test
    fun `a one-payee boost whose name has not landed does not borrow the chat name`() {
        val meta = MessageMetadata.DirectlySentCrypto(userId = knownUserId, boostedReaction = boost(fire))
        assertEquals("Boosted $fire", title(meta, "Sent", profiles = emptyMap(), chatName = "Design Team"))
    }

    @Test
    fun `a split boost keeps the amount, sign and no-counterparty avatar`() {
        val meta = MessageMetadata.DirectlySentCrypto(boostedReaction = boost(fire))
        val msg = feedMessage(metadata = meta)
        val item = mapper.map(ActivityFeedMessageWithToken(msg, token = usdfToken()) to emptyMap())

        assertEquals("-", item.signedAmountPrefix)
        assertEquals(TransactionAvatar.TokenIcon(usdfToken()), item.avatar)
        assertEquals(msg.amount, item.amount)
    }
}
