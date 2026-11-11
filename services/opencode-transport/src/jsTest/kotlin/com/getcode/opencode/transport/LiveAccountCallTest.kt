package com.getcode.opencode.transport

import com.codeinc.opencode.wire.account.v1.GetTokenAccountInfosResponse
import com.codeinc.opencode.wire.account.v1.IsOcpAccountResponse
import com.codeinc.opencode.wire.common.v1.Signature
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Signed calls from Node to the production OCP server, over [NodeGrpcTransport].
 *
 * Skipped unless `OCP_LIVE=1`. Phrases and keys are generated per run, except the optional
 * `FLIPCASH_TEST_MNEMONIC` account, whose phrase and keys are never printed.
 */
class LiveAccountCallTest {

    private val live = env("OCP_LIVE") == "1"

    @Test
    fun isOcpAccountAcceptsOurSignatureAndRejectsATamperedOne() = runTest(timeout = 45.seconds) {
        if (!live) return@runTest
        val transport = NodeGrpcTransport(OCP_HOST)
        try {
            // The owner key comes from a new recovery phrase through the same derivation the app
            // uses (m/44'/501'/0'/0'), so this exercises mnemonic + Ed25519 on Node.
            val owner = MnemonicPhrase.generate().getSolanaKeyPair(DerivePath.primary)

            // A fresh key is not an OCP account; NOT_FOUND means the server accepted the signature.
            val response = AccountClient(transport).isOcpAccount(owner)
            println("IsOcpAccount, fresh key, valid signature: ${response.result}")
            assertEquals(IsOcpAccountResponse.Result.NOT_FOUND, response.result)

            // Negative control: the same request with one signature bit flipped.
            val request = SignedRequests.isOcpAccount(owner)
            val bytes = request.signature!!.value_.toByteArray().also { it[0] = (it[0].toInt() xor 1).toByte() }
            val tampered = request.copy(signature = Signature(value_ = bytes.toByteString()))
            val error = runCatching { transport.unary(AccountClient.IS_OCP_ACCOUNT, tampered.encode()) }
                .exceptionOrNull() as? GrpcStatusException
                ?: fail("tampered signature was not rejected with a gRPC status")
            println("IsOcpAccount, fresh key, tampered signature: ${error.message}")
            assertEquals(GRPC_UNAUTHENTICATED, error.code)
        } finally {
            transport.close()
        }
    }

    @Test
    fun getTokenAccountInfosForTestAccount() = runTest(timeout = 45.seconds) {
        val words = env("FLIPCASH_TEST_MNEMONIC")
        if (!live || words.isNullOrBlank()) return@runTest
        val mnemonic = requireNotNull(MnemonicPhrase.newInstance(words.trim().split(Regex("\\s+")))) {
            "FLIPCASH_TEST_MNEMONIC is not a valid phrase"
        }
        val owner = DerivedKey.derive(DerivePath.primary, mnemonic).keyPair
        val transport = NodeGrpcTransport(OCP_HOST)
        try {
            val response = AccountClient(transport).getTokenAccountInfos(owner)
            val mints = response.token_account_infos.values.mapNotNull { it.mint?.value_?.size }.size
            println("GetTokenAccountInfos: ${response.result}, ${response.token_account_infos.size} token accounts ($mints with a mint)")
            assertEquals(GetTokenAccountInfosResponse.Result.OK, response.result)
            assertTrue(response.token_account_infos.isNotEmpty())
        } finally {
            transport.close()
        }
    }

    private companion object {
        const val OCP_HOST = "ocp-v2.api.flipcash-infra.net"
        const val GRPC_UNAUTHENTICATED = 16
    }
}

private fun env(name: String): String? = js("process.env[name]") as String?
