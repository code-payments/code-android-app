package com.getcode.opencode.internal.proto

import com.codeinc.opencode.gen.account.v1.OcpAccountService.TokenAccountInfo
import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService.Mint
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService.SocialLink
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.GetLimitsResponse
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.SendLimit
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.SubmitIntentResponse
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Round-trips generated messages through the protobuf wire codec.
 *
 * The mirror of the same test in `:services:flipcash`, covering the other generated artifact. The
 * classes arrive precompiled in `com.flipcash:ocp-client-protocol`, built against the protobuf
 * version that repo pinned, while the app resolves the protobuf runtime from its own version
 * catalog. The two move independently, and nothing asserts they agree — the `RuntimeVersion` check
 * that guards full gencode is not emitted for lite. The rest of the suite builds proto messages and
 * reads fields back, which exercises the accessors but never the encoder or the parser, so a codec
 * regression would pass unnoticed.
 *
 * These cover the paths that a runtime/gencode skew would break: nested and repeated messages,
 * enums, varints at their boundaries, fixed-width floats and doubles, non-ASCII strings, bytes,
 * maps, oneofs, well-known types, and unknown-field retention.
 */
class ProtoWireFormatTest {

    @Test
    fun `round-trips nested, repeated, enum and scalar fields`() {
        val original = TokenAccountInfo.newBuilder()
            .setAddress(solanaAccountId(1))
            .setOwner(solanaAccountId(2))
            .setAccountType(Model.AccountType.POOL)
            .setManagementState(TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKED)
            .setIndex(Long.MAX_VALUE)
            .setBalance(1_000_000_000L)
            .setUsdCostBasis(1234.5678)
            .setIsGiftCardIssuer(true)
            .setCreatedAt(
                Timestamp.newBuilder().setSeconds(1_764_000_000L).setNanos(123_456_789).build()
            )
            .setMintMetadata(
                Mint.newBuilder()
                    .setAddress(solanaAccountId(3))
                    .setDecimals(9)
                    .setName("café ☕ 🧊")
                    .setSymbol("OCP")
                    .addSocialLinks(
                        SocialLink.newBuilder()
                            .setX(SocialLink.X.newBuilder().setUsername("opencode").build())
                            .build()
                    )
                    .addSocialLinks(
                        SocialLink.newBuilder()
                            .setWebsite(
                                SocialLink.Website.newBuilder()
                                    .setUrl("https://example.invalid")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val decoded = TokenAccountInfo.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        // Asserted individually so a broken equals() cannot make the check above vacuous.
        assertEquals(Model.AccountType.POOL, decoded.accountType)
        assertEquals(
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKED,
            decoded.managementState
        )
        assertEquals(Long.MAX_VALUE, decoded.index)
        assertEquals(1234.5678, decoded.usdCostBasis, 0.0)
        assertEquals(123_456_789, decoded.createdAt.nanos)
        assertEquals("café ☕ 🧊", decoded.mintMetadata.name)
        assertEquals(2, decoded.mintMetadata.socialLinksCount)
        assertEquals("opencode", decoded.mintMetadata.getSocialLinks(0).x.username)
    }

    @Test
    fun `round-trips a oneof and keeps the case that was set`() {
        val original = SubmitIntentResponse.newBuilder()
            .setSuccess(
                SubmitIntentResponse.Success.newBuilder()
                    .setCode(SubmitIntentResponse.Success.Code.OK)
                    .build()
            )
            .build()

        val decoded = SubmitIntentResponse.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        assertEquals(SubmitIntentResponse.ResponseCase.SUCCESS, decoded.responseCase)
        assertEquals(SubmitIntentResponse.Success.Code.OK, decoded.success.code)
    }

    @Test
    fun `round-trips map fields`() {
        val original = GetLimitsResponse.newBuilder()
            .setResult(GetLimitsResponse.Result.OK)
            .setUsdTransacted(42.5)
            .putSendLimitsByCurrency("usd", sendLimit(250.0f, 1000.0f))
            .putSendLimitsByCurrency("eur", sendLimit(200.0f, 800.0f))
            .build()

        val decoded = GetLimitsResponse.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        assertEquals(2, decoded.sendLimitsByCurrencyCount)
        assertEquals(1000.0f, decoded.sendLimitsByCurrencyMap.getValue("usd").maxPerDay, 0.0f)
        assertEquals(200.0f, decoded.sendLimitsByCurrencyMap.getValue("eur").maxPerTransaction, 0.0f)
    }

    @Test
    fun `round-trips bytes across the full byte range`() {
        val payload = bytes(256) { it }
        val original = Model.SolanaAccountId.newBuilder().setValue(payload).build()

        val decoded = Model.SolanaAccountId.parseFrom(original.toByteArray())

        assertEquals(payload, decoded.value)
    }

    @Test
    fun `preserves unknown fields when re-encoding`() {
        val original = Mint.newBuilder()
            .setDecimals(9)
            .setName("forward compatible")
            .setSymbol("OCP")
            .build()

        // Empty declares no fields, so every field lands in the unknown-field set. Re-encoding has
        // to emit them again for a client to survive a server that is ahead of it.
        val reEncoded = Empty.parseFrom(original.toByteArray()).toByteArray()

        assertEquals(original, Mint.parseFrom(reEncoded))
    }

    private fun sendLimit(perTransaction: Float, perDay: Float) = SendLimit.newBuilder()
        .setNextTransaction(perTransaction)
        .setMaxPerTransaction(perTransaction)
        .setMaxPerDay(perDay)
        .build()

    private fun solanaAccountId(seed: Int) = Model.SolanaAccountId.newBuilder()
        .setValue(bytes(32) { seed })
        .build()

    private fun bytes(size: Int, value: (Int) -> Int) =
        ByteString.copyFrom(ByteArray(size) { value(it).toByte() })
}
