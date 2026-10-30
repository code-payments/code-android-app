package com.getcode.opencode.utils.logging

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.paymentrequest.v1.PaymentrequestService.GetPaymentRequestResponse
import com.codeinc.opencode.gen.common.v1.Model
import com.getcode.utils.LiveSecrets
import com.getcode.vendor.Base58
import com.google.protobuf.ByteString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FormatProtoTest {

    private val idBytes = ByteArray(32) { (it + 1).toByte() }
    private val idBase58 = Base58.encode(idBytes)

    @AfterTest
    fun tearDown() {
        LiveSecrets.release(idBase58)
    }

    @Test
    fun `payment_intent_id in a GetPaymentRequest response is logged as its last six characters`() {
        val response = GetPaymentRequestResponse.newBuilder()
            .setResult(GetPaymentRequestResponse.Result.DENIED)
            .setPaymentIntentId(Common.IntentId.newBuilder().setValue(ByteString.copyFrom(idBytes)))
            .build()

        val line = formatProto(response)

        assertContains(line, "paymentIntentId=…${idBase58.takeLast(6)}")
        assertFalse(idBase58 in line, "full id leaked: $line")
        // The rest of the body is still logged (OK is the zero value, which the formatter skips).
        assertContains(line, "result=")
    }

    @Test
    fun `an intent id held in LiveSecrets is logged as its last six characters`() {
        val intentId = Model.IntentId.newBuilder().setValue(ByteString.copyFrom(idBytes)).build()
        LiveSecrets.hold(idBase58)

        assertEquals("…${idBase58.takeLast(6)}", formatProto(intentId))
    }

    @Test
    fun `an intent id is logged in full once released`() {
        val intentId = Model.IntentId.newBuilder().setValue(ByteString.copyFrom(idBytes)).build()
        LiveSecrets.hold(idBase58)
        LiveSecrets.release(idBase58)

        assertEquals(idBase58, formatProto(intentId))
    }
}
