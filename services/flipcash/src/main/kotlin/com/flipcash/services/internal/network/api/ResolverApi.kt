package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.phone.v1.Model
import com.codeinc.flipcash.gen.resolver.v1.ResolverGrpcKt
import com.codeinc.flipcash.gen.resolver.v1.validate
import com.flipcash.services.models.ResolveIdentifier
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.codeinc.flipcash.gen.resolver.v1.ResolverService as RpcResolverService
import com.codeinc.flipcash.gen.resolver.v1.Model as ResolverModel

@Singleton
internal class ResolverApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ResolverGrpcKt.ResolverCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun resolve(
        owner: KeyPair,
        identifier: ResolveIdentifier,
    ): RpcResolverService.ResolveResponse {
        val request = RpcResolverService.ResolveRequest.newBuilder()
            .setIdentifier(identifier.asProtoIdentifier())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.resolve(request)
        }
    }

    private fun ResolveIdentifier.asProtoIdentifier(): ResolverModel.Identifier {
        val builder = ResolverModel.Identifier.newBuilder()
        return when (this) {
            is ResolveIdentifier.Phone ->
                builder.setPhone(Model.PhoneNumber.newBuilder().setValue(phone.phoneNumber))
            is ResolveIdentifier.UserId ->
                builder.setUserId(userId.asUserId())
        }.build()
    }
}
