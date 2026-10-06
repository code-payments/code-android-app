package com.flipcash.services.repository

import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.blob.UploadReservation
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.flipcash.services.models.chat.ChatId
import com.getcode.ed25519.Ed25519

interface BlobStorageRepository {
    suspend fun getUploadPolicy(owner: Ed25519.KeyPair): Result<UploadPolicy>

    suspend fun initiateExternalUpload(
        mimeType: String,
        sizeBytes: Long,
        owner: Ed25519.KeyPair,
        // Set when the bytes are sealed for this chat; the server then holds ciphertext it can't
        // inspect and applies the policy's encrypted constraints.
        e2eeChat: ChatId? = null,
    ): Result<UploadReservation>

    suspend fun completeExternalUpload(blobId: BlobId, owner: Ed25519.KeyPair): Result<BlobStatus>

    suspend fun getBlobs(
        blobIds: List<BlobId>,
        owner: Ed25519.KeyPair,
        context: BlobAccessContext,
    ): Result<List<BlobState>>
}
