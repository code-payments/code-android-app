package com.getcode.oct24.internal.network.api

import com.codeinc.flipchat.gen.profile.v1.ProfileGrpc
import com.codeinc.flipchat.gen.profile.v1.ProfileService
import com.codeinc.flipchat.gen.profile.v1.ProfileService.GetProfileRequest
import com.codeinc.flipchat.gen.profile.v1.ProfileService.SetDisplayNameRequest
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.oct24.internal.annotations.FcManagedChannel
import com.getcode.oct24.internal.network.core.GrpcApi
import com.getcode.oct24.internal.network.extensions.toUserId
import com.getcode.oct24.internal.network.utils.authenticate
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ProfileApi @Inject constructor(
    @FcManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ProfileGrpc.newStub(managedChannel).withWaitForReady()

    fun getProfile(userId: ID): Flow<ProfileService.GetProfileResponse> {
        val request = GetProfileRequest.newBuilder()
            .setUserId(userId.toUserId())
            .build()

        return api::getProfile
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun setDisplayName(owner: KeyPair, displayName: String): Flow<ProfileService.SetDisplayNameResponse> {
        val request = SetDisplayNameRequest.newBuilder()
            .setDisplayName(displayName)
            .apply { authenticate(owner) }
            .build()

        return api::setDisplayName
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

}