package com.flipcash.services.repository

import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.blob.UploadReservation
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.getcode.ed25519.Ed25519

interface BlobStorageRepository {
    suspend fun getUploadPolicy(owner: Ed25519.KeyPair): Result<UploadPolicy>

    suspend fun initiateExternalUpload(
        mimeType: String,
        sizeBytes: Long,
        owner: Ed25519.KeyPair,
    ): Result<UploadReservation>

    suspend fun completeExternalUpload(blobId: BlobId, owner: Ed25519.KeyPair): Result<BlobStatus>

    suspend fun getBlobs(blobIds: List<BlobId>, owner: Ed25519.KeyPair): Result<List<BlobState>>
}
