package com.getcode.opencode.intents

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.api.intents.IntentMultiTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.VerifiedExchangeState
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.keys.ProgramDerivedAccount
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * GATE: [IntentMultiTransfer] (SendPublicMultiPayment) matches the canonical cross-platform vectors
 * in `test-vectors/multi_payment_intent.json` on every target: action count, order, ids, the
 * transfer fields, the compact message each action signs, its SHA-256, the ed25519 signature by the
 * source authority, and the domain metadata. `rejections` must each make `create` throw.
 *
 * Unlike the Android host test, nothing is mocked: the signer is the fixture's public test seed and
 * the source vault is the fixture's seed key.
 */
class MultiPaymentIntentVectorTest {

    private fun key(seed: Int) = PublicKey(List(32) { seed.toByte() })

    private fun mintOf(seed: Int) = Mint(List(32) { seed.toByte() })

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun amountLe8(value: Long): ByteArray = ByteArray(8) { ((value ushr (8 * it)) and 0xFF).toByte() }

    /** "0.800000" -> 800000, without floating point. */
    private fun sixDecimalQuarks(text: String): Long {
        val (whole, fraction) = (text.split('.') + "").let { it[0] to it[1] }
        return (whole + fraction.padEnd(6, '0').take(6)).toLong()
    }

    private fun JsonElement.seedKey(authority: PublicKey): PublicKey {
        val content = jsonPrimitive.content
        return if (content == "sourceAuthority") authority else key(content.toInt())
    }

    private class State(override val currencyCode: String, override val exchangeRate: Double) : VerifiedExchangeState {
        override val encodedRate: ByteArray = ByteArray(0)
        override val encodedReserve: ByteArray? = null
    }

    private class Case(
        val signer: Ed25519.KeyPair,
        val cluster: AccountCluster,
        val mint: Mint,
        val state: VerifiedExchangeState,
        val shares: List<IntentMultiTransfer.Share>,
    ) {
        fun create() = IntentMultiTransfer.create(
            shares = shares,
            mint = mint,
            sourceCluster = cluster,
            verifiedState = state,
        )
    }

    private fun load(v: JsonObject): Case {
        val signer = Ed25519.createKeyPair(v["signerSeed"]!!.jsonPrimitive.content.hexToBytes())
        val authority = PublicKey(v["sourceAuthority"]!!.jsonPrimitive.content.hexToBytes().toList())
        assertEquals(authority.byteArray.toHex(), signer.publicKeyBytes.toHex(), "signerSeed does not derive sourceAuthority")

        val cluster = AccountCluster(
            authority = DerivedKey(DerivePath.primary, signer),
            timelock = TimelockDerivedAccounts(
                owner = authority,
                state = ProgramDerivedAccount(key(0), 0),
                vault = ProgramDerivedAccount(key(v["sourceVaultSeed"]!!.jsonPrimitive.int), 0),
            ),
        )

        val vs = v["verifiedState"]!!.jsonObject
        val fx = vs["rate"]!!.jsonPrimitive.content.toDouble()
        val state = State(vs["currency"]!!.jsonPrimitive.content, fx)

        val shares = v["payments"]!!.jsonArray.map { it.jsonObject }.map { p ->
            val currency = CurrencyCode.valueOf(p["currency"]!!.jsonPrimitive.content.uppercase())
            IntentMultiTransfer.Share(
                destination = key(p["destinationSeed"]!!.jsonPrimitive.int),
                destinationOwner = p["destinationOwnerSeed"]!!.seedKey(authority),
                amount = LocalFiat(
                    underlyingTokenAmount = Fiat(quarks = p["quarks"]!!.jsonPrimitive.content.toLong(), currencyCode = CurrencyCode.USD),
                    nativeAmount = Fiat(quarks = sixDecimalQuarks(p["nativeAmount"]!!.jsonPrimitive.content), currencyCode = currency),
                    rate = Rate(fx = fx, currency = currency),
                    mint = mintOf(p["mintSeed"]!!.jsonPrimitive.int),
                ),
            )
        }
        return Case(signer, cluster, mintOf(v["mintSeed"]!!.jsonPrimitive.int), state, shares)
    }

    private fun resource(): JsonObject =
        Json.parseToJsonElement(readTestResource("multi_payment_intent.json")).jsonObject

    @Test
    fun multi_payment_intent_matches_canonical_vectors() {
        val vectors = resource()["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val c = load(v)
            val intent = c.create()

            val expected = v["expectedActions"]!!.jsonArray.map { it.jsonObject }
            assertEquals(expected.size, intent.actions.size, "action count mismatch for $name")

            // Apply the server's nonce/blockhash per action, as the SubmitIntent flow does.
            intent.apply(
                expected.map { e ->
                    ServerParameter(
                        actionId = e["id"]!!.jsonPrimitive.int,
                        parameter = null,
                        configs = listOf(
                            ServerParameter.Config(
                                nonce = key(e["nonceSeed"]!!.jsonPrimitive.int),
                                blockhash = key(e["nonceValueSeed"]!!.jsonPrimitive.int),
                            )
                        ),
                    )
                }
            )

            expected.forEachIndexed { i, e ->
                val label = "$name action $i"
                val action = assertIs<ActionPublicTransfer>(intent.actions[i])

                assertEquals(e["id"]!!.jsonPrimitive.int, action.id, "id mismatch for $label")
                assertEquals(key(e["sourceSeed"]!!.jsonPrimitive.int), action.source, "source mismatch for $label")
                assertEquals(e["authority"]!!.jsonPrimitive.content, action.signer.publicKeyBytes.toHex(), "authority mismatch for $label")
                assertEquals(key(e["destinationSeed"]!!.jsonPrimitive.int), action.destination, "destination mismatch for $label")
                assertEquals(mintOf(e["mintSeed"]!!.jsonPrimitive.int).byteArray.toHex(), action.mint.byteArray.toHex(), "mint mismatch for $label")
                assertEquals(e["amount"]!!.jsonPrimitive.content.toLong(), action.amount.quarks, "amount mismatch for $label")

                val args = assertIs<CompactMessageArgs.Transfer>(action.compactMessageArgs().single())
                val message = "transfer".encodeToByteArray() +
                    args.source.byteArray + args.destination.byteArray + amountLe8(args.amountInQuarks) +
                    args.nonce.byteArray + args.nonceValue.byteArray
                assertEquals(e["message"]!!.jsonPrimitive.content, message.toHex(), "message bytes mismatch for $label")

                val signed = action.compactMessages().single()
                assertEquals(e["sha256"]!!.jsonPrimitive.content, signed.toHex(), "signed sha256 mismatch for $label")
                assertEquals(e["sha256"]!!.jsonPrimitive.content, Sha256Hash.hash(message).toHex(), "sha256 of message mismatch for $label")

                val signature = action.signatures().single()
                assertEquals(e["signature"]!!.jsonPrimitive.content, signature.byteArray.toHex(), "signature mismatch for $label")
            }

            val em = v["expectedMetadata"]!!.jsonObject
            val metadata = assertIs<TransactionMetadata.SendPublicMultiPayment>(intent.metadata)
            assertEquals(key(em["sourceSeed"]!!.jsonPrimitive.int), metadata.source, "metadata source mismatch for $name")
            assertEquals(mintOf(em["mintSeed"]!!.jsonPrimitive.int).byteArray.toHex(), metadata.mint.byteArray.toHex(), "metadata mint mismatch for $name")
            val payments = em["payments"]!!.jsonArray.map { it.jsonObject }
            assertEquals(payments.size, metadata.payments.size, "metadata payment count mismatch for $name")
            metadata.payments.forEachIndexed { i, payment ->
                val p = payments[i]
                val label = "$name payment $i"
                assertEquals(key(p["destinationSeed"]!!.jsonPrimitive.int), payment.destination, "metadata destination mismatch for $label")
                assertEquals(key(p["destinationOwnerSeed"]!!.jsonPrimitive.int), payment.destinationOwner, "metadata destination_owner mismatch for $label")
                val exchange = payment.verifiedExchangeData!!
                assertEquals(p["quarks"]!!.jsonPrimitive.content.toLong(), exchange.quarks, "metadata quarks mismatch for $label")
                assertEquals(p["nativeAmount"]!!.jsonPrimitive.content.toDouble(), exchange.nativeAmount, "metadata native amount mismatch for $label")
                assertEquals(p["currency"]!!.jsonPrimitive.content, exchange.verifiedState.currencyCode, "metadata currency mismatch for $label")
            }
        }
    }

    @Test
    fun multi_payment_intent_rejects_canonical_invalid_cases() {
        val rejections = resource()["rejections"]!!.jsonArray
        assertTrue(rejections.isNotEmpty(), "no rejections loaded")

        for (el in rejections) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val c = load(v)
            assertFailsWith<IllegalArgumentException>("expected rejection (${v["expectedError"]!!.jsonPrimitive.content}) for $name") {
                c.create()
            }
        }
    }
}
