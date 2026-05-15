package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.model.moderation.ModerationRequest
import com.flipcash.services.models.ModerationResult
import javax.inject.Inject

class ImageModerationResponseMapper @Inject constructor(
    private val attestationMapper: ModerationAttestationMapper,
): Mapper<ModerationRequest.Image, ModerationResult.Image> {
    override fun map(from: ModerationRequest.Image): ModerationResult.Image {
        val (image, response) = from
        return ModerationResult.Image(
            image = image.toList(),
            isAllowed = response.isAllowed,
            attestation = attestationMapper.map(response.attestation),
            flaggedCategory = ModerationResult.FlaggedCategory.valueOf(response.flaggedCategory.name)
        )
    }
}