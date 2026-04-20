package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.moderation.v1.ModerationGrpcKt
import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.utils.toByteString
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ModerationApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = ModerationGrpcKt.ModerationCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun moderateText(text: String, owner: Ed25519.KeyPair): ModerationService.ModerateTextResponse {
        val request = ModerationService.ModerateTextRequest.newBuilder()
            .setText(text)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.moderateText(request)
        }
    }

    suspend fun moderateImage(imageBytes: ByteArray, owner: Ed25519.KeyPair): ModerationService.ModerateImageResponse {
        val request = ModerationService.ModerateImageRequest.newBuilder()
            .setImageData(imageBytes.toByteString())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.moderateImage(request)
        }
    }
}