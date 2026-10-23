package com.getcode.opencode.solana

import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.getcode.crypt.DerivedKey
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.IntentMultiTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * GATE: [IntentMultiTransfer] (SendPublicMultiPayment) must match the canonical cross-platform
 * vectors in `multi_payment_intent.json` (the iOS repo asserts the same file): action count, order,
 * ids, source/destination/amount, the compact message each action signs (and its SHA-256), and the
 * proto metadata.
 *
 * The compact message is produced by the real action: server nonce parameters are applied with
 * `IntentType.apply` (as the SubmitIntent flow does), then `ActionType.compactMessageArgs()` is
 * serialized with the same layout as `CompactMessageVectorTest` and `ActionType.compactMessages()`
 * (the SHA-256 the action hands to ed25519) is compared with the fixture hash.
 *
 * `rejections` lists inputs the builder must refuse; each must make `create` throw.
 *
 * The ed25519 key pair is mocked (the JNI does not load on the host JVM; ed25519 is gated by
 * ed25519.json), so the signature itself is not asserted. `Mint` accepts arbitrary key bytes, so the
 * fixture's `mintSeed` key is used directly.
 */
class MultiPaymentIntentVectorTest {

    private fun key(seed: Int) = PublicKey(List(32) { seed.toByte() })

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun amountLe8(value: Long): ByteArray = ByteArray(8) { ((value ushr (8 * it)) and 0xFF).toByte() }

    private fun mintOf(seed: Int) = Mint(List(32) { seed.toByte() })

    private fun JsonElement.seedKey(authority: PublicKey): PublicKey {
        val content = jsonPrimitive.content
        return if (content == "sourceAuthority") authority else key(content.toInt())
    }

    private class Case(
        val cluster: AccountCluster,
        val sourceVault: PublicKey,
        val authority: PublicKey,
        val mint: Mint,
        val state: VerifiedState,
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
        val sourceVault = key(v["sourceVaultSeed"]!!.jsonPrimitive.int)
        val authority = PublicKey(v["sourceAuthority"]!!.jsonPrimitive.content.hexToBytes().toList())
        val mint = mintOf(v["mintSeed"]!!.jsonPrimitive.int)

        val cluster: AccountCluster = mockk {
            val pair = mockk<Ed25519.KeyPair> { every { publicKeyBytes } returns authority.byteArray }
            val derived = mockk<DerivedKey> { every { keyPair } returns pair }
            every { this@mockk.authority } returns derived
            every { vaultPublicKey } returns sourceVault
            every { authorityPublicKey } returns authority
        }

        val vs = v["verifiedState"]!!.jsonObject
        val state = VerifiedState(
            rateProto = verifiedCoreMintFiatExchangeRate {
                exchangeRate = coreMintFiatExchangeRate {
                    currencyCode = vs["currency"]!!.jsonPrimitive.content
                    exchangeRate = vs["rate"]!!.jsonPrimitive.content.toDouble()
                }
            },
            reserveProto = null,
        )
        val fx = vs["rate"]!!.jsonPrimitive.content.toDouble()

        val shares = v["payments"]!!.jsonArray.map { it.jsonObject }.map { p ->
            val currency = CurrencyCode.valueOf(p["currency"]!!.jsonPrimitive.content.uppercase())
            IntentMultiTransfer.Share(
                destination = key(p["destinationSeed"]!!.jsonPrimitive.int),
                destinationOwner = p["destinationOwnerSeed"]!!.seedKey(authority),
                amount = LocalFiat(
                    underlyingTokenAmount = Fiat(quarks = p["quarks"]!!.jsonPrimitive.content.toLong(), currencyCode = CurrencyCode.USD),
                    nativeAmount = Fiat(
                        quarks = BigDecimal(p["nativeAmount"]!!.jsonPrimitive.content).movePointRight(6).toLong(),
                        currencyCode = currency,
                    ),
                    rate = Rate(fx = fx, currency = currency),
                    mint = mintOf(p["mintSeed"]!!.jsonPrimitive.int),
                ),
            )
        }
        return Case(cluster, sourceVault, authority, mint, state, shares)
    }

    private fun resource(): JsonObject {
        val text = javaClass.getResourceAsStream("/multi_payment_intent.json")!!.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(text).jsonObject
    }

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
                val action = intent.actions[i]
                val quarks = e["amount"]!!.jsonPrimitive.content.toLong()

                assertEquals(e["id"]!!.jsonPrimitive.int, action.id, "id mismatch for $label")

                val transfer = action.action().noPrivacyTransfer
                assertEquals(key(e["sourceSeed"]!!.jsonPrimitive.int).byteArray.toHex(), transfer.source.value.toByteArray().toHex(), "source mismatch for $label")
                assertEquals(e["authority"]!!.jsonPrimitive.content, transfer.authority.value.toByteArray().toHex(), "authority mismatch for $label")
                assertEquals(key(e["destinationSeed"]!!.jsonPrimitive.int).byteArray.toHex(), transfer.destination.value.toByteArray().toHex(), "destination mismatch for $label")
                assertEquals(mintOf(e["mintSeed"]!!.jsonPrimitive.int).byteArray.toHex(), transfer.mint.value.toByteArray().toHex(), "mint mismatch for $label")
                assertEquals(quarks, transfer.amount, "amount mismatch for $label")

                val args = action.compactMessageArgs().single()
                assertIs<CompactMessageArgs.Transfer>(args)
                val message = "transfer".toByteArray(Charsets.UTF_8) +
                    args.source.byteArray + args.destination.byteArray + amountLe8(args.amountInQuarks) +
                    args.nonce.byteArray + args.nonceValue.byteArray
                assertEquals(e["message"]!!.jsonPrimitive.content, message.toHex(), "message bytes mismatch for $label")

                // What the action hands to ed25519.sign.
                val signed = action.compactMessages().single()
                assertEquals(e["sha256"]!!.jsonPrimitive.content, signed.toHex(), "signed sha256 mismatch for $label")
                assertEquals(
                    e["sha256"]!!.jsonPrimitive.content,
                    MessageDigest.getInstance("SHA-256").digest(message).toHex(),
                    "sha256 of message mismatch for $label",
                )
            }

            val em = v["expectedMetadata"]!!.jsonObject
            val metadata = intent.metadata().sendPublicMultiPayment
            assertEquals(key(em["sourceSeed"]!!.jsonPrimitive.int).byteArray.toHex(), metadata.source.value.toByteArray().toHex(), "metadata source mismatch for $name")
            assertEquals(mintOf(em["mintSeed"]!!.jsonPrimitive.int).byteArray.toHex(), metadata.mint.value.toByteArray().toHex(), "metadata mint mismatch for $name")
            val payments = em["payments"]!!.jsonArray.map { it.jsonObject }
            assertEquals(payments.size, metadata.paymentsCount, "metadata payment count mismatch for $name")
            metadata.paymentsList.forEachIndexed { i, payment ->
                val p = payments[i]
                val label = "$name payment $i"
                assertEquals(key(p["destinationSeed"]!!.jsonPrimitive.int).byteArray.toHex(), payment.destination.value.toByteArray().toHex(), "metadata destination mismatch for $label")
                assertTrue(payment.hasDestinationOwner(), "destination_owner missing for $label")
                assertEquals(key(p["destinationOwnerSeed"]!!.jsonPrimitive.int).byteArray.toHex(), payment.destinationOwner.value.toByteArray().toHex(), "metadata destination_owner mismatch for $label")
                val exchange = payment.clientExchangeData
                assertEquals(p["quarks"]!!.jsonPrimitive.content.toLong(), exchange.quarks, "metadata quarks mismatch for $label")
                assertEquals(p["nativeAmount"]!!.jsonPrimitive.content.toDouble(), exchange.nativeAmount, 0.0, "metadata native amount mismatch for $label")
                assertEquals(p["currency"]!!.jsonPrimitive.content, exchange.coreMintFiatExchangeRate.exchangeRate.currencyCode, "metadata currency mismatch for $label")
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
