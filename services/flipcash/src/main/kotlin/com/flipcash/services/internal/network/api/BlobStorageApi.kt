package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.blob.v1.BlobStorageGrpcKt
import com.codeinc.flipcash.gen.blob.v1.BlobStorageService as RpcBlobStorageService
import com.codeinc.flipcash.gen.blob.v1.Model
import com.codeinc.flipcash.gen.blob.v1.validate
import com.codeinc.flipcash.gen.common.v1.Common
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.buildAuthenticated
import com.flipcash.services.models.chat.BlobAccessContext
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
            .buildAuthenticated(owner) { setAuth(it) }

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
            .buildAuthenticated(owner) { setAuth(it) }

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
            .buildAuthenticated(owner) { setAuth(it) }

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.completeExternalUpload(request)
        }
    }

    suspend fun getBlobs(
        blobIds: List<BlobId>,
        owner: Ed25519.KeyPair,
        context: BlobAccessContext,
    ): RpcBlobStorageService.GetBlobsResponse {
        val request = getBlobsRequest(blobIds, context) { authenticate(owner) }

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getBlobs(request)
        }
    }
}

/**
 * Builds a `GetBlobs` request with [context] already in place when [authenticate] runs.
 *
 * Order matters and isn't cosmetic: `authenticate` signs the message as built so far, while the
 * server verifies that signature against the whole request minus the auth field. Anything set
 * afterwards is in what the server checks and missing from what the client signed, so the call
 * comes back `UNAUTHENTICATED` — which for an access context means every re-mint of a blob the
 * caller doesn't own fails, and the avatar it was for stays on its BlurHash.
 *
 * [authenticate] is a parameter so the ordering can be asserted without a signing key.
 */
internal fun getBlobsRequest(
    blobIds: List<BlobId>,
    context: BlobAccessContext,
    authenticate: RpcBlobStorageService.GetBlobsRequest.Builder.() -> Common.Auth,
): RpcBlobStorageService.GetBlobsRequest =
    RpcBlobStorageService.GetBlobsRequest.newBuilder()
        .setBlobIds(
            Model.BlobIdBatch.newBuilder()
                .addAllBlobIds(blobIds.map { it.toProto() })
        )
        // Omitted for Owned: the server resolves the caller's own blobs without one, and a
        // scope the caller can't claim would only narrow the read.
        .apply { context.toProto()?.let { setContext(it) } }
        .apply { setAuth(authenticate()) }
        .build()

private fun BlobId.toProto(): Model.BlobId =
    Model.BlobId.newBuilder().setValue(bytes.toByteString()).build()

private fun BlobAccessContext.toProto(): Model.AccessContext? = when (this) {
    BlobAccessContext.Owned -> null
    is BlobAccessContext.Profile ->
        Model.AccessContext.newBuilder().setUserProfile(userId.asUserId()).build()
    is BlobAccessContext.Chat ->
        Model.AccessContext.newBuilder().setChat(chatId.asChatId()).build()
    is BlobAccessContext.ChatProfile ->
        Model.AccessContext.newBuilder().setChatProfile(chatId.asChatId()).build()
}
