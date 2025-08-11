package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.profile.v1.ProfileGrpcKt
import com.codeinc.flipcash.gen.profile.v1.ProfileService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.linkingToken
import com.flipcash.services.models.SocialAccountLinkRequest
import com.flipcash.services.models.SocialAccountUnlinkRequest
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.model.core.ID
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class ProfileApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ProfileGrpcKt.ProfileCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Gets the profile for a user
     */
    suspend fun getProfile(userId: ID, owner: Ed25519.KeyPair): ProfileService.GetProfileResponse {
        val request = ProfileService.GetProfileRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

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

        return withContext(Dispatchers.IO) {
            api.setDisplayName(request)
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