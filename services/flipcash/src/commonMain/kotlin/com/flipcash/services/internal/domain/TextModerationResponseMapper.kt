package com.flipcash.services.internal.domain

import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.model.moderation.ModerationRequest
import com.flipcash.services.models.ModerationResult
import javax.inject.Inject

class TextModerationResponseMapper @Inject constructor(
    private val attestationMapper: ModerationAttestationMapper,
): Mapper<ModerationRequest.Text, ModerationResult.Text> {
    override fun map(from: ModerationRequest.Text): ModerationResult.Text {
        val (text, response) = from
        return ModerationResult.Text(
            text = text,
            isAllowed = response.isAllowed,
            attestation = attestationMapper.map(response.attestation),
            flaggedCategory = ModerationResult.FlaggedCategory.valueOf(response.flaggedCategory.name)
        )
    }
}