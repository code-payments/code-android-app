package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toSignature
import com.flipcash.services.models.ModerationResult
import com.getcode.crypt.Sha256Hash
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject
import kotlin.time.Instant

class ModerationAttestationMapper @Inject constructor(): Mapper<ModerationService.ModerationAttestation, ModerationResult.Attestation> {
    override fun map(from: ModerationService.ModerationAttestation): ModerationResult.Attestation {
        return ModerationResult.Attestation(
            rawValue = from.toByteArray().toList(),
            hash = Sha256Hash.of(from.contentHash.toByteArray()),
            timestamp = Instant.fromEpochSeconds(from.timestamp.seconds * 1000L),
            userId = from.userId.toId(),
            attestor = from.attestor.toPublicKey(),
            signature = from.signature.toSignature()
        )
    }
}