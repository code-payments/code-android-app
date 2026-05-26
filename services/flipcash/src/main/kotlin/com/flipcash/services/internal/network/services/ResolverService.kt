package com.flipcash.services.internal.network.services

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.internal.network.api.ResolverApi
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.models.ResolveContactError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject
import com.codeinc.flipcash.gen.resolver.v1.ResolverService as RpcResolverService

internal class ResolverService @Inject constructor(
    private val api: ResolverApi,
) {
    suspend fun resolve(
        owner: KeyPair,
        phone: ContactMethod.Phone,
    ): Result<PublicKey> {
        return runCatching {
            api.resolve(owner, phone)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcResolverService.ResolveResponse.Result.OK ->
                        Result.success(response.resolution.address.toPublicKey())
                    RpcResolverService.ResolveResponse.Result.NOT_FOUND ->
                        Result.failure(ResolveContactError.NotFound())
                    RpcResolverService.ResolveResponse.Result.DENIED ->
                        Result.failure(ResolveContactError.Denied())
                    RpcResolverService.ResolveResponse.Result.UNRECOGNIZED ->
                        Result.failure(ResolveContactError.Unrecognized())
                    else -> Result.failure(ResolveContactError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { ResolveContactError.Other(cause = it) })
            }
        )
    }
}
