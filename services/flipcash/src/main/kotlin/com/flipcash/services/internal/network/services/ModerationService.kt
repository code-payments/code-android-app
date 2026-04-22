package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.flipcash.services.internal.network.api.ModerationApi
import com.getcode.opencode.utils.toValidationOrElse
import com.flipcash.services.models.ImageModerationError
import com.flipcash.services.models.TextModerationError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject

internal class ModerationService @Inject constructor(
    private val api: ModerationApi,
) {
    suspend fun moderateText(text: String, owner: Ed25519.KeyPair): Result<ModerationService.ModerateTextResponse> {
        return runCatching {
            api.moderateText(text, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ModerationService.ModerateTextResponse.Result.OK -> {
                        Result.success(response)
                    }
                    ModerationService.ModerateTextResponse.Result.DENIED -> Result.failure(TextModerationError.Denied())
                    ModerationService.ModerateTextResponse.Result.UNSUPPORTED_LANGUAGE -> Result.failure(TextModerationError.UnsupportedLanguage())
                    ModerationService.ModerateTextResponse.Result.UNRECOGNIZED -> Result.failure(TextModerationError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { TextModerationError.Other(cause = it) })
            }
        )
    }

    suspend fun moderateImage(imageBytes: ByteArray, owner: Ed25519.KeyPair): Result<ModerationService.ModerateImageResponse> {
        return runCatching {
            api.moderateImage(imageBytes, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ModerationService.ModerateImageResponse.Result.OK -> {
                        Result.success(response)
                    }
                    ModerationService.ModerateImageResponse.Result.DENIED -> Result.failure(ImageModerationError.Denied())
                    ModerationService.ModerateImageResponse.Result.UNSUPPORTED_FORMAT ->  Result.failure(ImageModerationError.UnsupportedFormat())
                    ModerationService.ModerateImageResponse.Result.UNRECOGNIZED ->  Result.failure(ImageModerationError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { ImageModerationError.Other(cause = it) })
            }
        )
    }
}