package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.account.v1.OcpAccountService
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.solana.keys.PublicKey
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Captures the signed requests Android sends, so the Wire builders in `:services:opencode-transport`
 * can be pinned to the same bytes on Node and iOS.
 *
 * The requests go through the real [AccountApi] (protobuf-javalite, `sign()` over `buildPartial()`)
 * into a channel that records the serialized message and fails the call, so nothing leaves the
 * process. Keys come from fixed, non-secret seeds.
 *
 * The fixture lives in `:services:opencode-transport`'s `commonTest/resources`. This test fails if
 * Android's bytes drift from it; run with `-Dfixture.update=true` to rewrite it.
 */
class SignedRequestFixtureTest {

    private val ownerSeed = ByteArray(32) { (it + 1).toByte() }
    private val requestingOwnerSeed = ByteArray(32) { (0x40 + it).toByte() }
    private val mint = ByteArray(32) { (0xC0 + it).toByte() }

    private val owner = Ed25519.createKeyPair(ownerSeed)
    private val requestingOwner = Ed25519.createKeyPair(requestingOwnerSeed)

    @Test
    fun androidSignedRequestsMatchFixture() = runBlocking {
        val channel = CapturingChannel()
        val api = AccountApi(channel)

        runCatching { api.isCodeAccount(owner) }
        val isOcpAccount = OcpAccountService.IsOcpAccountRequest.parseFrom(channel.takeOnly())

        runCatching {
            api.getTokenAccounts(owner, requestingOwner, AccountFilter.MintAddress(PublicKey(mint.toList())))
        }
        val getTokenAccountInfos = OcpAccountService.GetTokenAccountInfosRequest.parseFrom(channel.takeOnly())

        // The signed bytes are the request with every signature field cleared.
        val isOcpUnsigned = isOcpAccount.toBuilder().clearSignature().build().toByteArray()
        val tokenInfosUnsigned = getTokenAccountInfos.toBuilder()
            .clearSignature()
            .clearRequestingOwnerSignature()
            .build()
            .toByteArray()

        val isOcpSignature = isOcpAccount.signature.value.toByteArray()
        val ownerSignature = getTokenAccountInfos.signature.value.toByteArray()
        val requestingSignature = getTokenAccountInfos.requestingOwnerSignature.value.toByteArray()
        assertTrue(Ed25519.verify(isOcpSignature, isOcpUnsigned, owner.publicKeyBytes))
        assertTrue(Ed25519.verify(ownerSignature, tokenInfosUnsigned, owner.publicKeyBytes))
        assertTrue(Ed25519.verify(requestingSignature, tokenInfosUnsigned, requestingOwner.publicKeyBytes))

        val captured = buildJsonObject {
            put("ownerSeed", ownerSeed.hex())
            put("requestingOwnerSeed", requestingOwnerSeed.hex())
            put("mint", mint.hex())
            put("isOcpAccount", buildJsonObject {
                put("unsigned", isOcpUnsigned.hex())
                put("signature", isOcpSignature.hex())
                put("request", isOcpAccount.toByteArray().hex())
            })
            put("getTokenAccountInfosByMint", buildJsonObject {
                put("unsigned", tokenInfosUnsigned.hex())
                put("ownerSignature", ownerSignature.hex())
                put("requestingOwnerSignature", requestingSignature.hex())
                put("request", getTokenAccountInfos.toByteArray().hex())
            })
        }

        val file = File(requireNotNull(System.getProperty("signedRequestFixture")) { "signedRequestFixture not set" })
        if (System.getProperty("fixture.update") == "true") {
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), captured) + "\n")
        }
        val pinned = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(pinned, captured)
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Records each message's serialized bytes and closes the call with UNAVAILABLE. */
private class CapturingChannel : ManagedChannel() {
    private val captured = mutableListOf<ByteArray>()

    fun takeOnly(): ByteArray = captured.single().also { captured.clear() }

    override fun <Req, Resp> newCall(method: MethodDescriptor<Req, Resp>, options: CallOptions): ClientCall<Req, Resp> =
        object : ClientCall<Req, Resp>() {
            private var listener: Listener<Resp>? = null
            override fun start(responseListener: Listener<Resp>, headers: Metadata) { listener = responseListener }
            override fun request(numMessages: Int) = Unit
            override fun cancel(message: String?, cause: Throwable?) = Unit
            override fun sendMessage(message: Req) {
                captured += method.streamRequest(message).readBytes()
            }
            override fun halfClose() {
                listener?.onClose(Status.UNAVAILABLE.withDescription("captured, not sent"), Metadata())
            }
        }

    override fun authority(): String = "capture.invalid"
    override fun shutdown(): ManagedChannel = this
    override fun isShutdown(): Boolean = false
    override fun isTerminated(): Boolean = false
    override fun shutdownNow(): ManagedChannel = this
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}
