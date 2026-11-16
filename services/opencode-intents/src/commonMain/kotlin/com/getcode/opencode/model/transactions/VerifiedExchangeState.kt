package com.getcode.opencode.model.transactions

/**
 * The server-signed state an intent's exchange data cites: the core mint fiat exchange rate and,
 * for launchpad mints, the reserve state. The signed messages are kept as the bytes the server
 * sent, so each client re-embeds them in SubmitIntent without depending on a protobuf runtime here.
 */
interface VerifiedExchangeState {
    /** ISO 4217 code of the signed rate. */
    val currencyCode: String

    /** The signed core mint to fiat rate. */
    val exchangeRate: Double

    /** The encoded `VerifiedCoreMintFiatExchangeRate`, as received. */
    val encodedRate: ByteArray

    /** The encoded `VerifiedLaunchpadCurrencyReserveState`, or null when the mint has none. */
    val encodedReserve: ByteArray?
}
