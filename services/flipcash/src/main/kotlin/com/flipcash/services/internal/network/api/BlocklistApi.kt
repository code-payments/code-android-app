package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.blocklist.v1.BlocklistGrpcKt
import com.codeinc.flipcash.gen.blocklist.v1.BlocklistService
import com.codeinc.flipcash.gen.blocklist.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.model.core.ID
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BlocklistApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = BlocklistGrpcKt.BlocklistCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Adds a user to the caller's blocklist. Blocking a user that is already
     * blocked is a no-op and returns OK.
     */
    suspend fun blockUser(userId: ID, owner: KeyPair): BlocklistService.BlockUserResponse {
        val request = BlocklistService.BlockUserRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.blockUser(request)
        }
    }

    /**
     * Removes a user from the caller's blocklist. Unblocking a user that isn't
     * blocked is a no-op and returns OK.
     */
    suspend fun unblockUser(userId: ID, owner: KeyPair): BlocklistService.UnblockUserResponse {
        val request = BlocklistService.UnblockUserRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unblockUser(request)
        }
    }

    /**
     * Checks whether a user is on the caller's blocklist.
     */
    suspend fun isBlocked(userId: ID, owner: KeyPair): BlocklistService.IsBlockedResponse {
        val request = BlocklistService.IsBlockedRequest.newBuilder()
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.isBlocked(request)
        }
    }

    /**
     * Gets the caller's blocklist using a paging API, ordered by most recently
     * blocked first.
     */
    suspend fun getBlocklist(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): BlocklistService.GetBlocklistResponse {
        val request = BlocklistService.GetBlocklistRequest.newBuilder()
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getBlocklist(request)
        }
    }
}
