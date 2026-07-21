package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.BlobStorageService
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.blob.UploadReservation
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.flipcash.services.repository.BlobStorageRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalBlobStorageRepository(
    private val service: BlobStorageService,
) : BlobStorageRepository {

    override suspend fun getUploadPolicy(owner: Ed25519.KeyPair): Result<UploadPolicy> =
        service.getUploadPolicy(owner)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun initiateExternalUpload(
        mimeType: String,
        sizeBytes: Long,
        owner: Ed25519.KeyPair,
    ): Result<UploadReservation> = service.initiateExternalUpload(mimeType, sizeBytes, owner)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun completeExternalUpload(
        blobId: BlobId,
        owner: Ed25519.KeyPair,
    ): Result<BlobStatus> = service.completeExternalUpload(blobId, owner)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getBlobs(
        blobIds: List<BlobId>,
        owner: Ed25519.KeyPair,
    ): Result<List<BlobState>> = service.getBlobs(blobIds, owner)
        .onFailure { ErrorUtils.handleError(it) }
}
