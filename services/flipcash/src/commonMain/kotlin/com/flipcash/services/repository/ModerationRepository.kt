package com.flipcash.services.repository

import com.flipcash.services.models.ImageContentKind
import com.flipcash.services.models.ModerationResult
import com.flipcash.services.models.TextContentKind
import com.getcode.ed25519kmp.Ed25519

interface ModerationRepository {
    suspend fun moderateText(
        text: String,
        kind: TextContentKind,
        owner: Ed25519.KeyPair,
    ): Result<ModerationResult.Text>

    suspend fun moderateImage(
        imageBytes: ByteArray,
        kind: ImageContentKind,
        owner: Ed25519.KeyPair,
    ): Result<ModerationResult.Image>
}