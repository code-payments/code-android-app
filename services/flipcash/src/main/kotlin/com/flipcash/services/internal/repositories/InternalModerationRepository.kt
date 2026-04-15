package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ImageModerationResponseMapper
import com.flipcash.services.internal.domain.TextModerationResponseMapper
import com.flipcash.services.internal.model.moderation.ModerationRequest
import com.flipcash.services.internal.network.services.ModerationService
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.repository.ModerationRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalModerationRepository(
    private val service: ModerationService,
    private val textModerationResponseMapper: TextModerationResponseMapper,
    private val imageModerationResponseMapper: ImageModerationResponseMapper,
) : ModerationRepository {
    override suspend fun moderateText(text: String, owner: Ed25519.KeyPair): Result<ModerationResult.Text> {
        return service.moderateText(text, owner)
            .onFailure { ErrorUtils.handleError(it) }
            .map { ModerationRequest.Text(text, it) }
            .map { textModerationResponseMapper.map(it) }
    }

    override suspend fun moderateImage(imageBytes: ByteArray, owner: Ed25519.KeyPair): Result<ModerationResult.Image> {
        return service.moderateImage(imageBytes, owner)
            .onFailure { ErrorUtils.handleError(it) }
            .map { ModerationRequest.Image(imageBytes, it) }
            .map { imageModerationResponseMapper.map(it) }
    }
}