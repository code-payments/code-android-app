package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.extensions.sign
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins protobuf-java bytes and signatures for signed requests whose Wire `encode()` order differs
 * from field-number order, for `CanonicalEncodingTest` in :services:opencode-transport.
 *
 * Each case sets the fields that put a oneof member numbered below a regular field (or a regular
 * field numbered above a oneof) on the wire, and is signed the way the app signs today: over the
 * message built so far, without its own signature. Seeds are fixed and non-secret.
 *
 * Run with `-Dfixture.update=true` to rewrite the fixture.
 */
class CanonicalOrderFixtureTest {

    private val ownerSeed = ByteArray(32) { 3 }
    private val owner = Ed25519.createKeyPair(ownerSeed)

    @Test
    fun protobufJavaBytesMatchFixture() {
        val captured = buildJsonObject {
            put("ownerSeed", ownerSeed.hex())
            statefulSwapInitiate()
            statelessSwapInitiate()
            submitActionsWithServerExchangeDataAndAppMetadata()
        }
        val file = File(requireNotNull(System.getProperty("canonicalOrderFixture")) { "canonicalOrderFixture not set" })
        if (System.getProperty("fixture.update") == "true") {
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), captured) + "\n")
        }
        assertEquals(Json.parseToJsonElement(file.readText()).jsonObject, captured)
    }

    private fun JsonObjectBuilder.statefulSwapInitiate() {
        val initiate = OcpTransactionService.StatefulSwapRequest.Initiate.newBuilder()
            .setStablecoin(
                OcpTransactionService.StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
                    .setId(Model.SwapId.newBuilder().setValue(bytes(0x21)))
                    .setFromMint(account(0x22))
                    .setToMint(account(0x23))
                    .setSwapAmount(1_250_000)
                    .setFundingSource(OcpTransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT)
                    .setFundingId("funding-1")
                    .setDestinationOwner(account(0x24))
                    .setFeeAmount(2_500)
                    .setSwapVersion(2),
            )
            .setOwner(account(owner.publicKeyBytes))
            .setSwapAuthority(account(0x25))
            .setProofSignature(signature(0x26))
        val unsigned = initiate.buildPartial().toByteArray()
        initiate.setSignature(initiate.sign(owner))
        val request = OcpTransactionService.StatefulSwapRequest.newBuilder().setInitiate(initiate).build()
        record("statefulSwapInitiate", unsigned, request.initiate.signature.value, request)
    }

    private fun JsonObjectBuilder.statelessSwapInitiate() {
        val initiate = OcpTransactionService.StatelessSwapRequest.Initiate.newBuilder()
            .setStablecoin(
                OcpTransactionService.StatelessSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
                    .setFromMint(account(0x31))
                    .setToMint(account(0x32))
                    .setSwapAmount(990_000)
                    .setSwapVersion(1),
            )
            .setOwner(account(owner.publicKeyBytes))
            .setWaitForFinalization(true)
        val unsigned = initiate.buildPartial().toByteArray()
        initiate.setSignature(initiate.sign(owner))
        val request = OcpTransactionService.StatelessSwapRequest.newBuilder().setInitiate(initiate).build()
        record("statelessSwapInitiate", unsigned, request.initiate.signature.value, request)
    }

    /** The client sets neither field today, so its SubmitIntent bytes match Wire; this pins the case it would not. */
    private fun JsonObjectBuilder.submitActionsWithServerExchangeDataAndAppMetadata() {
        val actions = OcpTransactionService.SubmitIntentRequest.SubmitActions.newBuilder()
            .setId(Model.IntentId.newBuilder().setValue(bytes(0x41)))
            .setOwner(account(owner.publicKeyBytes))
            .setMetadata(
                OcpTransactionService.Metadata.newBuilder()
                    .setSendPublicPayment(
                        OcpTransactionService.SendPublicPaymentMetadata.newBuilder()
                            .setSource(account(0x42))
                            .setDestination(account(0x43))
                            .setDestinationOwner(account(0x44))
                            .setServerExchangeData(
                                OcpTransactionService.ExchangeData.newBuilder()
                                    .setCurrency("cad")
                                    .setExchangeRate(1.37)
                                    .setNativeAmount(13.7)
                                    .setQuarks(10_000_000)
                                    .setMint(account(0x45)),
                            )
                            .setIsWithdrawal(true)
                            .setIsIndirectSend(true)
                            .setMint(account(0x45)),
                    )
                    .setAppMetadata(OcpTransactionService.AppMetadata.newBuilder().setValue(bytes(0x46, 8))),
            )
        val unsigned = actions.buildPartial().toByteArray()
        actions.setSignature(actions.sign(owner))
        val request = OcpTransactionService.SubmitIntentRequest.newBuilder().setSubmitActions(actions).build()
        record("submitActionsServerExchangeData", unsigned, request.submitActions.signature.value, request)
    }

    private fun JsonObjectBuilder.record(name: String, unsigned: ByteArray, signature: ByteString, request: MessageLite) =
        put(name, buildJsonObject {
            put("unsigned", unsigned.hex())
            put("signature", signature.toByteArray().hex())
            put("request", request.toByteArray().hex())
        })

    private fun bytes(seed: Int, size: Int = 32): ByteString = ByteString.copyFrom(ByteArray(size) { (seed + it).toByte() })
    private fun account(seed: Int) = Model.SolanaAccountId.newBuilder().setValue(bytes(seed)).build()
    private fun account(key: ByteArray) = Model.SolanaAccountId.newBuilder().setValue(ByteString.copyFrom(key)).build()
    private fun signature(seed: Int) = Model.Signature.newBuilder().setValue(bytes(seed, 64)).build()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
