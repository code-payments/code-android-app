package com.getcode.opencode.parity

import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.solana.keys.KeyType
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58

// One rendering of the domain values both OCP paths decode to. It is compiled into
// :services:opencode's jvmTest (protobuf-java) and :services:opencode-client's commonTest (Wire), so
// the pinned strings mean the same thing on both sides. Keys render as base58, signatures as hex,
// and doubles as their raw bits because Kotlin/JS prints 1.0 as "1".

fun renderLiveMintData(value: LiveMintDataResponse?): String = when (value) {
    null -> "none"
    is LiveMintDataResponse.ExchangeRates -> value.rates.joinToString(prefix = "rates[", postfix = "]") {
        "${it.rate.currency.name}:${it.rate.fx.bits()}@${it.timestamp}/${it.signature.hex()}"
    }
    is LiveMintDataResponse.LaunchpadReserveState -> value.reserveStates.joinToString(prefix = "reserves[", postfix = "]") {
        "${it.reserveState.mint.base58()}:${it.reserveState.currentSupply}@${it.reserveState.timestamp}/${it.signature.hex()}"
    }
}

fun renderLimits(limits: Limits): String = buildString {
    append("since=${limits.sinceDate} fetch=${limits.fetchDate} ")
    append("usd=${limits.amountUsdTransactedSinceConsumption.quarks}/${limits.amountUsdTransactedSinceConsumption.currencyCode.name}")
    CurrencyCode.entries.forEach { code ->
        limits.sendLimitFor(code)?.let {
            append(" ${code.name}=${it.nextTransaction.bits()},${it.maxPerTransaction.bits()},${it.maxPerDay.bits()}")
        }
    }
}

/** `null` is NOT_FOUND. */
fun renderAccountInfos(accounts: Map<PublicKey, AccountInfo>?): String =
    accounts?.entries?.sortedBy { it.key.base58() }?.joinToString("\n") { (key, info) ->
        "${key.base58()}=${renderAccountInfo(info)}"
    } ?: "not-found"

fun renderAccountInfo(info: AccountInfo): String = listOf(
    "index=${info.index}",
    "type=${info.accountType}",
    "address=${info.address.base58()}",
    "owner=${info.owner?.base58()}",
    "authority=${info.authority?.base58()}",
    "source=${info.balanceSource.name}",
    "balance=${info.balance}",
    "management=${info.managementState.name}",
    "blockchain=${info.blockchainState.name}",
    "claim=${info.claimState.name}",
    "exchange=${renderExchange(info.originalExchangeData)}",
    "mint=${info.mint.base58()}",
    "createdAt=${info.createdAt}",
    "giftCardIssuer=${info.isGiftCardIssuer}",
    "usdCostBasis=${info.usdCostBasis.bits()}",
).joinToString(" ")

private fun renderExchange(data: ExchangeData.WithRate): String =
    "${data.currencyCode}:${data.exchangeRate.bits()}:${data.nativeAmount.bits()}:${data.quarks}:${data.mint.base58()}"

fun renderServerParameters(parameters: List<ServerParameter>): String = parameters.joinToString("\n") { p ->
    val parameter = when (val it = p.parameter) {
        null -> "none"
        is ServerParameter.Parameter.FeePayment -> "fee:${it.publicKey.hex()}"
    }
    "action=${p.actionId} $parameter " + p.configs.joinToString { "${it.nonce.base58()}/${it.blockhash.base58()}" }
}

/** The error a SubmitIntent response maps to: the case name, then the reasons Android keeps. */
fun renderSubmitIntentError(case: String, details: List<String>): String = "$case$details"

private fun Double.bits(): String = toRawBits().toString(16)
private fun KeyType.hex(): String = byteArray.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
