package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.profile.v1.ProfileGrpcKt
import com.codeinc.flipcash.gen.profile.v1.ProfileService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.asUsername
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.linkingToken
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.flipcash.services.models.chat.BlobId
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.utils.toByteString
import com.codeinc.flipcash.gen.profile.v1.validate
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ProfileApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ProfileGrpcKt.ProfileCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Gets the profile for a user, keyed by either their user ID or their username.
     */
    suspend fun getProfile(
        identifier: ProfileIdentifier,
        owner: Ed25519.KeyPair,
    ): ProfileService.GetProfileResponse {
        val request = ProfileService.GetProfileRequest.newBuilder()
            .apply {
                when (identifier) {
                    is ProfileIdentifier.UserId -> setUserId(identifier.userId.asUserId())
                    is ProfileIdentifier.Username -> setUsername(identifier.username.asUsername())
                }
            }
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getProfile(request)
        }
    }

    /**
     * Sets the display name for a user
     */
    suspend fun setDisplayName(
        displayName: String,
        owner: Ed25519.KeyPair
    ): ProfileService.SetDisplayNameResponse {
        val request = ProfileService.SetDisplayNameRequest.newBuilder()
            .setDisplayName(displayName)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.setDisplayName(request)
        }
    }

    /**
     * Sets the caller's profile picture to a blob they have already uploaded via
     * BlobStorage. The server derives the DISPLAY/THUMBNAIL renditions and returns
     * the full set.
     */
    suspend fun setProfilePicture(
        blobId: BlobId,
        owner: Ed25519.KeyPair,
    ): ProfileService.SetProfilePictureResponse {
        val request = ProfileService.SetProfilePictureRequest.newBuilder()
            .setBlobId(
                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                    .setValue(blobId.bytes.toByteString())
            )
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.setProfilePicture(request)
        }
    }

    /**
     * links a social account to a user
     */
    suspend fun linkSocialAccount(
        request: SocialAccountLinkRequest,
        owner: Ed25519.KeyPair
    ): ProfileService.LinkSocialAccountResponse {
        val apiRequest = ProfileService.LinkSocialAccountRequest.newBuilder()
            .setLinkingToken(request.linkingToken())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.linkSocialAccount(apiRequest)
        }
    }

    /**
     * Updates the caller's tip card customization with the given hex color.
     */
    suspend fun updateTipCard(
        owner: Ed25519.KeyPair,
        hexColor: String,
    ): ProfileService.UpdateTipCardResponse {
        val request = ProfileService.UpdateTipCardRequest.newBuilder()
            .setColor(
                Common.Color.newBuilder()
                    .setHex(hexColor)
                    .build()
            )
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.updateTipCard(request)
        }
    }

    /**
     * removes a social account link from a user
     */
    suspend fun unlinkSocialAccount(
        request: SocialAccountUnlinkRequest,
        owner: Ed25519.KeyPair
    ): ProfileService.UnlinkSocialAccountResponse {
        val builder = ProfileService.UnlinkSocialAccountRequest.newBuilder()

        when (request) {
            is SocialAccountUnlinkRequest.X -> builder.setXUserId(request.userId)
        }

        val apiRequest = builder.apply { setAuth(authenticate(owner)) }.build()

        return withContext(Dispatchers.IO) {
            api.unlinkSocialAccount(apiRequest)
        }
    }
}