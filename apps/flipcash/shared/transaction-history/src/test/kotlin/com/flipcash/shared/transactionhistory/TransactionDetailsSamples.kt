package com.flipcash.shared.transactionhistory

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import kotlin.time.Instant

/**
 * Fixtures for the details-screen renders. Held in the test source set: they exist to pin the
 * states down visually, not to ship.
 *
 * Subtitles are spelled out here rather than resolved through [TransactionSubtitles], which needs a
 * `ResourceHelper` the fixtures have no reason to stand up; they must stay in step with
 * `strings.xml`, which is what these renders are for.
 */
internal object TransactionDetailsSamples {

    val At: Instant = Instant.parse("2026-08-29T18:42:00Z")

    /** A destination account, as the receipt row shortens it. */
    private const val Account = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

    private fun mint(byte: Byte) = Mint(List(32) { byte })

    /**
     * Token art is served per-mint by the backend, so the fixtures name a file the screenshot
     * test's image loader answers from `src/test/resources/tokens/` — the tokens' own icons,
     * through the same [com.flipcash.app.core.ui.TokenIcon] path the app uses.
     */
    private fun token(name: String, symbol: String, address: Mint, image: String): Token = MintMetadata(
        address = address,
        decimals = 6,
        name = name,
        symbol = symbol,
        createdAt = At,
        description = "",
        imageUrl = "https://example.invalid/tokens/$image",
        vmMetadata = MintMetadata.usdf.vmMetadata,
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    val Jeffy: Token = token("Jeffy", "JEFFY", mint(1), "jeffy.png")

    /** The reserve — the real mint, since the screen keys off it the way the rest of the app does. */
    val Dollars: Token = token("Dollars", "USDF", Mint.usdf, "dollars.webp")

    /** No profile picture, so the avatar draws her initials — the app's real no-photo state. */
    val Sally = UserProfile.Empty.copy(displayName = "Sally The Streamer")

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD)

    private fun base(
        kind: TransactionKind,
        avatar: TransactionAvatar,
        amount: Fiat?,
        token: Token?,
        tokenAmount: String?,
        subtitle: String?,
        prefix: String?,
        heading: String? = null,
    ) = TransactionDetails(
        id = "5KJp7z2Fh9qVYc3mXbNs1LtR8dGw4eA6uQnW",
        kind = kind,
        avatar = avatar,
        heading = heading,
        subtitle = subtitle,
        signedAmountPrefix = prefix,
        amount = amount,
        timestamp = At,
        token = token,
        status = TransactionStatus.Completed,
        currencyCode = amount?.currencyCode?.name,
        exchangeRate = amount?.let { 1.0 },
        tokenAmount = tokenAmount,
    )

    /** The person is the heading; the sign on the amount is what says which way it went. */
    private fun person(kind: TransactionKind, amount: Double, prefix: String) = base(
        kind = kind,
        avatar = TransactionAvatar.Profile(Sally, badgeToken = Jeffy),
        amount = usd(amount),
        token = Jeffy,
        tokenAmount = "1,204.905",
        heading = Sally.displayName,
        subtitle = null,
        prefix = prefix,
    ).copy(canViewInChat = true)

    val Tipped = person(TransactionKind.Tipped, 20.0, "-")
    val ReceivedFromPerson = person(TransactionKind.Received, 5.0, "+")
    val SentToPerson = person(TransactionKind.Sent, 12.50, "-")

    /** A bill handed over face to face — nobody to name, so the line says how it moved. */
    val GaveCash = base(
        kind = TransactionKind.GaveCash,
        avatar = TransactionAvatar.TokenIcon(Jeffy),
        amount = usd(3.00),
        token = Jeffy,
        tokenAmount = "180.735",
        subtitle = "In Person",
        prefix = "-",
    )

    val ReceivedCash = base(
        kind = TransactionKind.ReceivedCash,
        avatar = TransactionAvatar.TokenIcon(Dollars),
        amount = usd(1.00),
        token = Dollars,
        tokenAmount = "1.000000",
        subtitle = "In Person",
        prefix = "+",
    )

    /** A link somebody has already opened. Its heading names it, so there is no line under it. */
    val SentCashLink = base(
        kind = TransactionKind.SentCashLink,
        avatar = TransactionAvatar.TokenIcon(Jeffy),
        amount = usd(7.50),
        token = Jeffy,
        tokenAmount = "451.838",
        subtitle = null,
        prefix = "-",
    )

    val Buy = base(
        kind = TransactionKind.Buy,
        avatar = TransactionAvatar.TokenIcon(Jeffy),
        amount = usd(50.00),
        token = Jeffy,
        tokenAmount = "3,012.264",
        subtitle = "with Dollars",
        prefix = "+",
    )

    val Sell = base(
        kind = TransactionKind.Sell,
        avatar = TransactionAvatar.TokenIcon(Jeffy),
        amount = usd(18.20),
        token = Jeffy,
        tokenAmount = "1,096.463",
        subtitle = "for Dollars",
        prefix = "-",
    )

    val Withdraw = base(
        kind = TransactionKind.Withdraw,
        avatar = TransactionAvatar.TokenIcon(Dollars),
        amount = usd(120.00),
        token = Dollars,
        tokenAmount = "120.000000",
        subtitle = null,
        prefix = "-",
    ).copy(account = TransactionAccount(Account, TransactionAccount.Direction.To))

    val Deposit = base(
        kind = TransactionKind.Deposit,
        avatar = TransactionAvatar.TokenIcon(Dollars),
        amount = usd(250.00),
        token = Dollars,
        tokenAmount = "250.000000",
        subtitle = null,
        prefix = "+",
    ).copy(account = TransactionAccount(Account, TransactionAccount.Direction.From))

    val Convert = base(
        kind = TransactionKind.Convert,
        avatar = TransactionAvatar.SwapTokens(from = Dollars, to = Jeffy),
        amount = usd(40.00),
        token = Dollars,
        tokenAmount = "40.000000",
        subtitle = "Dollars → Jeffy",
        prefix = "-",
    ).copy(
        toToken = Jeffy,
        fee = usd(0.40),
        received = usd(39.60),
    )

    /** No metadata at all: no amount, so no currency, rate or token quantity to state. */
    val Unknown = base(
        kind = TransactionKind.Unknown,
        avatar = TransactionAvatar.Generic(),
        amount = null,
        token = null,
        tokenAmount = null,
        subtitle = null,
        prefix = null,
    ).copy(status = TransactionStatus.Unknown)

    /** A link nobody has opened yet, so it can still be pulled back from the app bar. */
    val OpenCashLink = SentCashLink.copy(
        status = TransactionStatus.Pending,
        canCancel = true,
    )

    /** Every state, in the order the renders are laid out. */
    val All: List<Pair<String, TransactionDetails>> = listOf(
        "01_you_tipped" to Tipped,
        "02_you_received" to ReceivedFromPerson,
        "03_you_sent" to SentToPerson,
        "04_you_gave_cash" to GaveCash,
        "05_you_received_cash" to ReceivedCash,
        "06_you_sent_cash_link" to SentCashLink,
        "07_buy" to Buy,
        "08_sell" to Sell,
        "09_withdraw" to Withdraw,
        "10_deposit" to Deposit,
        "11_convert" to Convert,
        "12_unknown" to Unknown,
        "13_open_cash_link" to OpenCashLink,
    )
}
