package com.getcode.opencode.client

import com.codeinc.opencode.wire.account.v1.GetTokenAccountInfosResponse
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.transport.AccountClient
import com.getcode.opencode.transport.GrpcTransport
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/** Token account infos from `ocp.account.v1.Account/GetTokenAccountInfos`, as domain values. */
class AccountInfoClient(transport: GrpcTransport) {

    private val accounts = AccountClient(transport)

    /**
     * The owner's token accounts keyed by address, or [AccountInfosResult.NotFound]. Accounts whose
     * type or states this client does not recognise are dropped, as on Android.
     */
    suspend fun getAccountInfos(
        owner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair = owner,
        mint: Mint? = null,
    ): AccountInfosResult =
        AccountInfoResponses.decode(accounts.getTokenAccountInfos(owner, requestingOwner, mint?.byteArray))

    companion object {
        const val GET_TOKEN_ACCOUNT_INFOS = "/ocp.account.v1.Account/GetTokenAccountInfos"
    }
}

sealed interface AccountInfosResult {
    data class Found(val accounts: Map<PublicKey, AccountInfo>) : AccountInfosResult
    data object NotFound : AccountInfosResult
}

internal object AccountInfoResponses {
    fun decode(bytes: ByteArray) = decode(GetTokenAccountInfosResponse.ADAPTER.decode(bytes))

    fun decode(response: GetTokenAccountInfosResponse): AccountInfosResult = when (response.result) {
        GetTokenAccountInfosResponse.Result.OK -> AccountInfosResult.Found(
            response.token_account_infos.mapNotNull { (base58, info) ->
                info.toDomain()?.let { PublicKey.fromBase58(base58) to it }
            }.toMap(),
        )
        GetTokenAccountInfosResponse.Result.NOT_FOUND -> AccountInfosResult.NotFound
    }
}
