package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.blob.v1.BlobStorageService as RpcBlobStorageService
import com.flipcash.services.internal.network.api.BlobStorageApi
import com.flipcash.services.internal.network.extensions.toBlobState
import com.flipcash.services.internal.network.extensions.toBlobStatus
import com.flipcash.services.internal.network.extensions.toUploadPolicy
import com.flipcash.services.internal.network.extensions.toUploadTarget
import com.flipcash.services.models.CompleteExternalUploadError
import com.flipcash.services.models.GetBlobsError
import com.flipcash.services.models.GetUploadPolicyError
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.blob.UploadReservation
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobState
import com.flipcash.services.models.chat.BlobStatus
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject

internal class BlobStorageService @Inject constructor(
    private val api: BlobStorageApi,
) {

    suspend fun getUploadPolicy(owner: Ed25519.KeyPair): Result<UploadPolicy> {
        return runCatching { api.getUploadPolicy(owner) }
            .foldWithSuppression(
                onSuccess = { response ->
                    when (response.result) {
                        RpcBlobStorageService.GetUploadPolicyResponse.Result.OK ->
                            Result.success(response.policy.toUploadPolicy())
                        RpcBlobStorageService.GetUploadPolicyResponse.Result.DENIED ->
                            Result.failure(GetUploadPolicyError.Denied())
                        else -> Result.failure(GetUploadPolicyError.Unrecognized())
                    }
                },
                onFailure = { cause ->
                    Result.failure(cause.toValidationOrElse { GetUploadPolicyError.Other(cause = it) })
                }
            )
    }

    suspend fun initiateExternalUpload(
        mimeType: String,
        sizeBytes: Long,
        owner: Ed25519.KeyPair,
    ): Result<UploadReservation> {
        return runCatching { api.initiateExternalUpload(mimeType, sizeBytes, owner) }
            .foldWithSuppression(
                onSuccess = { response ->
                    when (response.result) {
                        RpcBlobStorageService.InitiateExternalUploadResponse.Result.OK ->
                            Result.success(
                                UploadReservation(
                                    blobId = BlobId(response.blobId.value.toByteArray()),
                                    target = response.uploadTarget.toUploadTarget(),
                                )
                            )
                        RpcBlobStorageService.InitiateExternalUploadResponse.Result.UNSUPPORTED_TYPE ->
                            Result.failure(InitiateExternalUploadError.UnsupportedType(response.policyVersionOrNull()))
                        RpcBlobStorageService.InitiateExternalUploadResponse.Result.TOO_LARGE ->
                            Result.failure(InitiateExternalUploadError.TooLarge(response.policyVersionOrNull()))
                        RpcBlobStorageService.InitiateExternalUploadResponse.Result.QUOTA_EXCEEDED ->
                            Result.failure(InitiateExternalUploadError.QuotaExceeded())
                        RpcBlobStorageService.InitiateExternalUploadResponse.Result.DENIED ->
                            Result.failure(InitiateExternalUploadError.Denied())
                        else -> Result.failure(InitiateExternalUploadError.Unrecognized())
                    }
                },
                onFailure = { cause ->
                    Result.failure(cause.toValidationOrElse { InitiateExternalUploadError.Other(cause = it) })
                }
            )
    }

    suspend fun completeExternalUpload(
        blobId: BlobId,
        owner: Ed25519.KeyPair,
    ): Result<BlobStatus> {
        return runCatching { api.completeExternalUpload(blobId, owner) }
            .foldWithSuppression(
                onSuccess = { response ->
                    when (response.result) {
                        RpcBlobStorageService.CompleteExternalUploadResponse.Result.OK ->
                            Result.success(response.status.toBlobStatus())
                        RpcBlobStorageService.CompleteExternalUploadResponse.Result.NOT_FOUND ->
                            Result.failure(CompleteExternalUploadError.NotFound())
                        RpcBlobStorageService.CompleteExternalUploadResponse.Result.NOT_UPLOADED ->
                            Result.failure(CompleteExternalUploadError.NotUploaded())
                        else -> Result.failure(CompleteExternalUploadError.Unrecognized())
                    }
                },
                onFailure = { cause ->
                    Result.failure(cause.toValidationOrElse { CompleteExternalUploadError.Other(cause = it) })
                }
            )
    }

    suspend fun getBlobs(
        blobIds: List<BlobId>,
        owner: Ed25519.KeyPair,
    ): Result<List<BlobState>> {
        return runCatching { api.getBlobs(blobIds, owner) }
            .foldWithSuppression(
                onSuccess = { response ->
                    when (response.result) {
                        RpcBlobStorageService.GetBlobsResponse.Result.OK ->
                            Result.success(
                                // Drop non-terminal blobs (toBlobState → null) so a still-processing
                                // id resolves to an empty list and the caller keeps polling.
                                if (response.hasBlobs()) response.blobs.blobsList.mapNotNull { it.toBlobState() }
                                else emptyList()
                            )
                        RpcBlobStorageService.GetBlobsResponse.Result.DENIED ->
                            Result.failure(GetBlobsError.Denied())
                        else -> Result.failure(GetBlobsError.Unrecognized())
                    }
                },
                onFailure = { cause ->
                    Result.failure(cause.toValidationOrElse { GetBlobsError.Other(cause = it) })
                }
            )
    }

    private fun RpcBlobStorageService.InitiateExternalUploadResponse.policyVersionOrNull(): String? =
        if (hasPolicyVersion()) policyVersion.value else null
}
