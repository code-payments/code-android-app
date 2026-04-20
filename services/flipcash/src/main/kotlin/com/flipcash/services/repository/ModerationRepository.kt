package com.flipcash.services.repository

import com.flipcash.services.models.ModerationResult
import com.getcode.ed25519.Ed25519

interface ModerationRepository {
    suspend fun moderateText(text: String, owner: Ed25519.KeyPair): Result<ModerationResult.Text>
    suspend fun moderateImage(imageBytes: ByteArray, owner: Ed25519.KeyPair): Result<ModerationResult.Image>
}