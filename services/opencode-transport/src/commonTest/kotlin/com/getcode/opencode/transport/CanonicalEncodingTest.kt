package com.getcode.opencode.transport

import com.codeinc.opencode.wire.common.v1.IntentId
import com.codeinc.opencode.wire.common.v1.Signature
import com.codeinc.opencode.wire.common.v1.SolanaAccountId
import com.codeinc.opencode.wire.common.v1.SwapId
import com.codeinc.opencode.wire.transaction.v1.AppMetadata
import com.codeinc.opencode.wire.transaction.v1.ExchangeData
import com.codeinc.opencode.wire.transaction.v1.FundingSource
import com.codeinc.opencode.wire.transaction.v1.GetLimitsResponse
import com.codeinc.opencode.wire.transaction.v1.Metadata
import com.codeinc.opencode.wire.transaction.v1.SendLimit
import com.codeinc.opencode.wire.transaction.v1.SendPublicPaymentMetadata
import com.codeinc.opencode.wire.transaction.v1.StatefulSwapRequest
import com.codeinc.opencode.wire.transaction.v1.StatelessSwapRequest
import com.codeinc.opencode.wire.transaction.v1.SubmitIntentRequest
import com.getcode.ed25519kmp.Ed25519
import com.getcode.wire.canonical.CanonicalField
import com.getcode.wire.canonical.CanonicalSchema
import com.getcode.wire.canonical.canonicalize
import com.squareup.wire.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The canonical encoder against protobuf-java (`canonical_order.json`, pinned by
 * CanonicalOrderFixtureTest in :services:opencode) for requests where Wire's own `encode()` order
 * differs from field-number order.
 *
 * Ed25519 is deterministic, so a signature over the canonical bytes equal to the one protobuf-java's
 * bytes produced is independent proof that both signed the same message.
 */
class CanonicalEncodingTest {
    private val fixture = Json.parseToJsonElement(readTestResource("canonical_order.json")).jsonObject
    private val owner = Ed25519.createKeyPair(fixture.bytes("ownerSeed"))

    @Test
    fun statefulSwapInitiateMatchesProtobufJava() {
        val unsigned = StatefulSwapRequest.Initiate(
            stablecoin = StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters(
                id = SwapId(value_ = bytes(0x21)),
                from_mint = account(0x22),
                to_mint = account(0x23),
                swap_amount = 1_250_000,
                funding_source = FundingSource.FUNDING_SOURCE_SUBMIT_INTENT,
                funding_id = "funding-1",
                destination_owner = account(0x24),
                fee_amount = 2_500,
                swap_version = 2,
            ),
            owner = SolanaAccountId(value_ = owner.publicKeyBytes.toByteString()),
            swap_authority = account(0x25),
            proof_signature = Signature(value_ = bytes(0x26, 64)),
        )
        assertSignedLikeProtobufJava(
            fixture.section("statefulSwapInitiate"),
            unsigned,
            sign = { unsigned.copy(signature = it) },
            request = { StatefulSwapRequest(initiate = it) },
            decodeSigned = { bytes ->
                val initiate = StatefulSwapRequest.ADAPTER.decode(bytes).initiate!!
                initiate.signature!!.value_ to initiate.copy(signature = null)
            },
        )
    }

    @Test
    fun statelessSwapInitiateMatchesProtobufJava() {
        val unsigned = StatelessSwapRequest.Initiate(
            stablecoin = StatelessSwapRequest.Initiate.CoinbaseStableSwapperClientParameters(
                from_mint = account(0x31),
                to_mint = account(0x32),
                swap_amount = 990_000,
                swap_version = 1,
            ),
            owner = SolanaAccountId(value_ = owner.publicKeyBytes.toByteString()),
            wait_for_finalization = true,
        )
        assertSignedLikeProtobufJava(
            fixture.section("statelessSwapInitiate"),
            unsigned,
            sign = { unsigned.copy(signature = it) },
            request = { StatelessSwapRequest(initiate = it) },
            decodeSigned = { bytes ->
                val initiate = StatelessSwapRequest.ADAPTER.decode(bytes).initiate!!
                initiate.signature!!.value_ to initiate.copy(signature = null)
            },
        )
    }

    @Test
    fun submitActionsWithServerExchangeDataMatchesProtobufJava() {
        val mint = account(0x45)
        val unsigned = SubmitIntentRequest.SubmitActions(
            id = IntentId(value_ = bytes(0x41)),
            owner = SolanaAccountId(value_ = owner.publicKeyBytes.toByteString()),
            metadata = Metadata(
                send_public_payment = SendPublicPaymentMetadata(
                    source = account(0x42),
                    destination = account(0x43),
                    destination_owner = account(0x44),
                    server_exchange_data = ExchangeData(
                        currency = "cad",
                        exchange_rate = 1.37,
                        native_amount = 13.7,
                        quarks = 10_000_000,
                        mint = mint,
                    ),
                    is_withdrawal = true,
                    is_indirect_send = true,
                    mint = mint,
                ),
                app_metadata = AppMetadata(value_ = bytes(0x46, 8)),
            ),
        )
        assertSignedLikeProtobufJava(
            fixture.section("submitActionsServerExchangeData"),
            unsigned,
            sign = { unsigned.copy(signature = it) },
            request = { SubmitIntentRequest(submit_actions = it) },
            decodeSigned = { bytes ->
                val actions = SubmitIntentRequest.ADAPTER.decode(bytes).submit_actions!!
                actions.signature!!.value_ to actions.copy(signature = null)
            },
        )
    }

    /** Map entries come out sorted by key bytes, the order Go's ForceConsistentMarshal uses. */
    @Test
    fun mapEntriesAreSortedByKey() {
        fun limit(next: Float) = SendLimit(next_transaction = next)
        val unsorted = GetLimitsResponse(send_limits_by_currency = linkedMapOf("usd" to limit(1f), "cad" to limit(2f), "aud" to limit(3f)))
        val sorted = GetLimitsResponse(send_limits_by_currency = linkedMapOf("aud" to limit(3f), "cad" to limit(2f), "usd" to limit(1f)))
        assertNotEquals(sorted.encode().hex(), unsorted.encode().hex())
        assertEquals(sorted.encode().hex(), unsorted.encodeCanonical().hex())
    }

    /** Unknown fields go after every known field, as the server's marshal writes them. */
    @Test
    fun unknownFieldsGoLast() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to CanonicalField.Scalar, 3 to CanonicalField.Scalar)))
        // field 3 = 1, field 2 (unknown) = 2, field 1 = 3
        val input = byteArrayOf(0x18, 1, 0x10, 2, 0x08, 3)
        assertContentEquals(byteArrayOf(0x08, 3, 0x18, 1, 0x10, 2), canonicalize(input, "t", schema))
    }

    private fun <U : Message<U, *>, S : Message<S, *>, R : Message<R, *>> assertSignedLikeProtobufJava(
        pinned: JsonObject,
        unsigned: U,
        sign: (Signature) -> S,
        request: (S) -> R,
        decodeSigned: (ByteArray) -> Pair<ByteString, Message<*, *>>,
    ) {
        val canonical = unsigned.encodeCanonical()
        assertNotEquals(pinned.text("unsigned"), unsigned.encode().hex(), "Wire's encode() order should differ here")
        assertEquals(pinned.text("unsigned"), canonical.hex())
        assertCanonicalInvariants(unsigned)

        val signature = Ed25519.sign(canonical, owner)
        assertEquals(pinned.text("signature"), signature.hex())
        val signed = request(sign(Signature(value_ = signature.toByteString())))
        assertEquals(pinned.text("request"), signed.encodeCanonical().hex())
        assertCanonicalInvariants(signed)

        // Verify the way the server does: decode, clear the signature, re-marshal canonically.
        val (received, cleared) = decodeSigned(pinned.bytes("request"))
        assertTrue(Ed25519.verify(received.toByteArray(), cleared.encodeCanonical(), owner.publicKeyBytes))
        // A flipped bit in the signature must fail the same check.
        val tampered = received.toByteArray().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertTrue(!Ed25519.verify(tampered, cleared.encodeCanonical(), owner.publicKeyBytes))
    }

    private fun <M : Message<M, *>> assertCanonicalInvariants(message: M) {
        val canonical = message.encodeCanonical()
        assertEquals(message.encode().size, canonical.size)
        assertEquals(message, message.adapter.decode(canonical))
        val type = message.adapter.typeUrl!!.substringAfterLast('/')
        assertContentEquals(canonical, canonicalize(canonical, type, OcpCanonicalSchema))
    }

    private fun bytes(seed: Int, size: Int = 32): ByteString = ByteArray(size) { (seed + it).toByte() }.toByteString()
    private fun account(seed: Int) = SolanaAccountId(value_ = bytes(seed))
}

private fun JsonObject.section(name: String): JsonObject = getValue(name).jsonObject
private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun JsonObject.bytes(name: String): ByteArray =
    text(name).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
