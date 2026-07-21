package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.blob.v1.BlobStorageGrpcKt
import com.codeinc.flipcash.gen.blob.v1.BlobStorageService as RpcBlobStorageService
import com.codeinc.flipcash.gen.blob.v1.Model
import com.codeinc.flipcash.gen.blob.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.chat.BlobId
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.utils.toByteString
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the BlobStorage gRPC service — direct-to-storage uploads. The bytes never travel through
 * gRPC: [initiateExternalUpload] reserves a [Model.BlobId] and returns a presigned target the client
 * uploads to over plain HTTP, [completeExternalUpload] advises the server the upload finished, and
 * [getBlobs] resolves ids to their status + a fresh download URL.
 */
@Singleton
internal class BlobStorageApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = BlobStorageGrpcKt.BlobStorageCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun getUploadPolicy(owner: Ed25519.KeyPair): RpcBlobStorageService.GetUploadPolicyResponse {
        val request = RpcBlobStorageService.GetUploadPolicyRequest.newBuilder()
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getUploadPolicy(request)
        }
    }

    suspend fun initiateExternalUpload(
        mimeType: String,
        sizeBytes: Long,
        owner: Ed25519.KeyPair,
    ): RpcBlobStorageService.InitiateExternalUploadResponse {
        val request = RpcBlobStorageService.InitiateExternalUploadRequest.newBuilder()
            .setMimeType(mimeType)
            .setSizeBytes(sizeBytes)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.initiateExternalUpload(request)
        }
    }

    suspend fun completeExternalUpload(
        blobId: BlobId,
        owner: Ed25519.KeyPair,
    ): RpcBlobStorageService.CompleteExternalUploadResponse {
        val request = RpcBlobStorageService.CompleteExternalUploadRequest.newBuilder()
            .setBlobId(blobId.toProto())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.completeExternalUpload(request)
        }
    }

    suspend fun getBlobs(
        blobIds: List<BlobId>,
        owner: Ed25519.KeyPair,
    ): RpcBlobStorageService.GetBlobsResponse {
        val request = RpcBlobStorageService.GetBlobsRequest.newBuilder()
            .setBlobIds(
                Model.BlobIdBatch.newBuilder()
                    .addAllBlobIds(blobIds.map { it.toProto() })
            )
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getBlobs(request)
        }
    }

    private fun BlobId.toProto(): Model.BlobId =
        Model.BlobId.newBuilder().setValue(bytes.toByteString()).build()
}
