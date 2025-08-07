package com.getcode.solana.rpc

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.solana.keys.base64
import com.solana.networking.Rpc20Driver
import com.solana.rpccore.JsonRpc20Request
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.sol4k.Connection

class SolanaConnection(
    val rpcUrl: String,
) {
    private val connection = Connection(rpcUrl)
    suspend fun getLatestBlockhash(): String = connection.getLatestBlockhash()
}

suspend fun Rpc20Driver.doesAccountExist(publicKey: PublicKey): Result<Unit> {
    val request = JsonRpc20Request(
        method = "getAccountInfo",
        params = buildJsonArray {
            add(publicKey.base58())
            addJsonObject { put("encoding", "base64") }
        },
        id = "1"
    )
    val response = makeRequest(request, JsonElement.serializer())
    val error = response.error
    if (error != null) {
        return Result.failure(RpcException(error.code, error.message))
    }

    val accountInfo = response.result?.jsonObject?.get("value")?.takeIf { it !is JsonNull }
    if (accountInfo == null) {
        return Result.failure(Throwable("Missing account info"))
    }

    return Result.success(Unit)
}

suspend fun Rpc20Driver.sendTransaction(encodedTransaction: String): Result<String> {
    println("sending transaction on the blockchain => $encodedTransaction")
    val request = JsonRpc20Request(
        method = "sendTransaction",
        params = buildJsonArray { add(encodedTransaction) },
        id = "1"
    )
    val response = makeRequest(request, JsonElement.serializer())
    val error = response.error
    if (error != null) {
        return Result.failure(RpcException(error.code, error.message))
    }

    val signature = response.result
        ?: return Result.failure(Throwable("Missing signature"))

    return Result.success(signature.toString())
}

suspend fun Rpc20Driver.simulateTransaction(encodedTransaction: String): Result<String> {
    println("simulating transaction on the blockchain => $encodedTransaction")
    val request = JsonRpc20Request(
        method = "simulateTransaction",
        params = buildJsonArray {
            add(encodedTransaction)
            addJsonObject { put("encoding", "base64") }
        },
        id = "1"
    )
    val response = makeRequest(request, JsonElement.serializer())
    val error = response.error
    if (error != null) {
        val errorDetails = response.result?.jsonObject?.get("err")?.toString() ?: "Unknown error"
        val logs = response.result?.jsonObject?.get("logs")?.toString() ?: "No logs available"
        return Result.failure(
            RpcException(
                error.code,
                "${error.message}, err: $errorDetails, logs: $logs"
            )
        )
    }

    val signature = response.result?.jsonObject?.get("result")
        ?: return Result.failure(Throwable("Missing signature"))

    return Result.success(signature.toString())
}

class RpcException(
    val code: Int,
    override val message: String,
) : Throwable(message)