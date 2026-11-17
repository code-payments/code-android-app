package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.moderation.v1.Model
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ModerationAttestationMapperTest {

    private val mapper = ModerationAttestationMapper()

    @Test
    fun `maps timestamp from protobuf seconds and nanos`() {
        val proto = Model.ModerationAttestation.newBuilder()
            .setContentHash(ByteString.copyFrom(ByteArray(32) { 1 }))
            .setTimestamp(Timestamp.newBuilder().setSeconds(1_763_251_200L).setNanos(123_000_000))
            .setUserId(Common.UserId.newBuilder().setValue(ByteString.copyFrom(ByteArray(16) { 2 })))
            .setAttestor(Common.PublicKey.newBuilder().setValue(ByteString.copyFrom(ByteArray(32) { 3 })))
            .setSignature(Common.Signature.newBuilder().setValue(ByteString.copyFrom(ByteArray(64) { 4 })))
            .build()

        val result = mapper.map(proto)

        assertEquals(Instant.fromEpochSeconds(1_763_251_200L, 123_000_000), result.timestamp)
    }
}
