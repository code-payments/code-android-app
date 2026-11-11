package com.getcode.opencode.parity

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.api.intents.IntentCreateAccount
import com.getcode.opencode.internal.network.api.intents.IntentDistribution
import com.getcode.opencode.internal.network.api.intents.IntentRemoteReceive
import com.getcode.opencode.internal.network.api.intents.IntentTransfer
import com.getcode.opencode.internal.network.api.intents.IntentWithdraw
import com.getcode.opencode.internal.network.api.intents.actions.ActionFeePayment
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fee
import com.getcode.opencode.model.financial.FeeType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.transactions.VerifiedExchangeState
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * Intents for the SubmitIntent parity fixture, one per action and metadata branch the Wire
 * encoding has beyond the multi-payment intent. `OcpClientFixtureTest` builds them with
 * protobuf-java's verified state and `OcpClientParityTest` with Wire's; everything else is the same
 * fixed, non-secret seeds.
 *
 * The remote-send intent is left out: it needs a [com.getcode.opencode.model.financial.Token], and
 * its metadata and actions are the single-send and transfer branches covered here.
 */
fun parityIntents(
    owner: Ed25519.KeyPair,
    mint: Mint,
    verifiedState: VerifiedExchangeState,
): List<Pair<String, IntentType>> {
    val cluster = cluster(owner, state = 0, vault = 16)
    val amount = localFiat(mint, quarks = 500_000, native = 685_000)
    // A gift card's mnemonic is never encoded, so an empty phrase stands in; only its cluster is used.
    val giftCard = GiftCardAccount(
        mnemonic = MnemonicPhrase(MnemonicPhrase.Kind.L12, emptyList()),
        cluster = cluster(Ed25519.createKeyPair(ByteArray(32) { 2 }), state = 3, vault = 19),
    )
    return listOf(
        "transfer" to IntentTransfer.create(
            amount = amount,
            mint = mint,
            sourceCluster = cluster,
            destination = key(35),
            destinationOwner = key(51),
            verifiedState = verifiedState,
            intentId = key(0x78),
        ),
        "withdrawWithFee" to IntentWithdraw.create(
            amount = amount,
            mint = mint,
            sourceCluster = cluster,
            destination = key(36),
            destinationOwner = key(52),
            fee = Fee(Fiat(quarks = 10_000, currencyCode = CurrencyCode.USD), FeeType.CreateOnSendWithdrawal),
            verifiedState = verifiedState,
            intentId = key(0x79),
        ),
        "withdrawNoOwner" to IntentWithdraw.create(
            amount = amount,
            mint = mint,
            sourceCluster = cluster,
            destination = key(37),
            destinationOwner = null,
            verifiedState = verifiedState,
            intentId = key(0x7A),
        ),
        "createAccount" to IntentCreateAccount.createUserAccount(cluster, mint, intentId = key(0x7B)),
        "remoteReceive" to IntentRemoteReceive.create(
            giftCard = giftCard,
            owner = cluster,
            amount = amount,
            mint = mint,
            intentId = key(0x7C),
        ),
        "distribution" to IntentDistribution.create(
            owner = cluster,
            source = cluster,
            distributions = listOf(
                Distribution(key(38), Fiat(quarks = 300_000, currencyCode = CurrencyCode.USD)),
                Distribution(key(39), Fiat(quarks = 200_000, currencyCode = CurrencyCode.USD)),
            ),
            mint = mint,
            intentId = key(0x7D),
        ),
    )
}

/**
 * One server parameter per action, as the server returns them: a nonce for each, and the fee
 * destination for fee payments. Both sides apply these before encoding SubmitSignatures.
 */
fun parityServerParameters(intent: IntentType): List<ServerParameter> =
    intent.actions.mapIndexed { index, action ->
        ServerParameter(
            actionId = action.id,
            parameter = if (action is ActionFeePayment) ServerParameter.Parameter.FeePayment(key(0x60 + index)) else null,
            configs = listOf(ServerParameter.Config(nonce = key(0x40 + index), blockhash = Hash(List(32) { (0x50 + index).toByte() }))),
        )
    }

private fun cluster(owner: Ed25519.KeyPair, state: Int, vault: Int) = AccountCluster(
    authority = DerivedKey(DerivePath.primary, owner),
    timelock = TimelockDerivedAccounts(
        owner = PublicKey(owner.publicKeyBytes.toList()),
        state = ProgramDerivedAccount(key(state), 0),
        vault = ProgramDerivedAccount(key(vault), 0),
    ),
)

private fun localFiat(mint: Mint, quarks: Long, native: Long) = LocalFiat(
    underlyingTokenAmount = Fiat(quarks = quarks, currencyCode = CurrencyCode.USD),
    nativeAmount = Fiat(quarks = native, currencyCode = CurrencyCode.CAD),
    rate = Rate(fx = 1.37, currency = CurrencyCode.CAD),
    mint = mint,
)

private fun key(seed: Int) = PublicKey(List(32) { seed.toByte() })
