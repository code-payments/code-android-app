package com.getcode.opencode.transport

import com.codeinc.opencode.wire.account.v1.GetTokenAccountInfosResponse
import com.codeinc.opencode.wire.account.v1.IsOcpAccountResponse
import com.getcode.ed25519kmp.Ed25519

/** Signed, read-only calls against `ocp.account.v1.Account`. */
class AccountClient(private val transport: GrpcTransport) {

    suspend fun isOcpAccount(owner: Ed25519.KeyPair): IsOcpAccountResponse {
        val request = SignedRequests.isOcpAccount(owner)
        return IsOcpAccountResponse.ADAPTER.decode(transport.unary(IS_OCP_ACCOUNT, request.encode()))
    }

    suspend fun getTokenAccountInfos(
        owner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair = owner,
        filterByMint: ByteArray? = null,
    ): GetTokenAccountInfosResponse {
        val request = SignedRequests.getTokenAccountInfos(owner, requestingOwner, filterByMint)
        return GetTokenAccountInfosResponse.ADAPTER.decode(
            transport.unary(GET_TOKEN_ACCOUNT_INFOS, request.encode()),
        )
    }

    internal companion object {
        const val IS_OCP_ACCOUNT = "/ocp.account.v1.Account/IsOcpAccount"
        const val GET_TOKEN_ACCOUNT_INFOS = "/ocp.account.v1.Account/GetTokenAccountInfos"
    }
}
