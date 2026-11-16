package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.moderation.v1.Model
import com.codeinc.flipcash.gen.moderation.v1.ModerationGrpcKt
import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.codeinc.flipcash.gen.moderation.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.ImageContentKind
import com.flipcash.services.models.TextContentKind
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.flipcash.services.internal.network.extensions.toByteString
import dev.bmcreations.protovalidate.orThrow
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

    suspend fun moderateText(
        text: String,
        kind: TextContentKind,
        owner: Ed25519.KeyPair,
    ): ModerationService.ModerateTextResponse {
        val request = moderateTextRequest(text, kind) { authenticate(owner) }

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.moderateText(request)
        }
    }

    suspend fun moderateImage(
        imageBytes: ByteArray,
        kind: ImageContentKind,
        owner: Ed25519.KeyPair,
    ): ModerationService.ModerateImageResponse {
        val request = moderateImageRequest(imageBytes, kind) { authenticate(owner) }

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.moderateImage(request)
        }
    }
}

/**
 * Builds a `ModerateText` request with [kind] set before [authenticate] runs. `authenticate` signs
 * the message as built so far and the server verifies against the whole request minus the auth,
 * so a kind set afterwards would come back `UNAUTHENTICATED`.
 *
 * [authenticate] is a parameter so the ordering can be asserted without a signing key.
 */
internal fun moderateTextRequest(
    text: String,
    kind: TextContentKind,
    authenticate: ModerationService.ModerateTextRequest.Builder.() -> Common.Auth,
): ModerationService.ModerateTextRequest =
    ModerationService.ModerateTextRequest.newBuilder()
        .setText(text)
        .setKind(kind.toProto())
        .apply { setAuth(authenticate()) }
        .build()

/** Builds a `ModerateImage` request with [kind] set before [authenticate] runs; see [moderateTextRequest]. */
internal fun moderateImageRequest(
    imageBytes: ByteArray,
    kind: ImageContentKind,
    authenticate: ModerationService.ModerateImageRequest.Builder.() -> Common.Auth,
): ModerationService.ModerateImageRequest =
    ModerationService.ModerateImageRequest.newBuilder()
        .setImageData(imageBytes.toByteString())
        .setKind(kind.toProto())
        .apply { setAuth(authenticate()) }
        .build()

private fun TextContentKind.toProto(): Model.ContentKind = when (this) {
    TextContentKind.CurrencyName -> Model.ContentKind.CURRENCY_NAME
    TextContentKind.CurrencySymbol -> Model.ContentKind.CURRENCY_SYMBOL
    TextContentKind.CurrencyDescription -> Model.ContentKind.CURRENCY_DESCRIPTION
}

private fun ImageContentKind.toProto(): Model.ContentKind = when (this) {
    ImageContentKind.CurrencyIcon -> Model.ContentKind.CURRENCY_ICON
}
