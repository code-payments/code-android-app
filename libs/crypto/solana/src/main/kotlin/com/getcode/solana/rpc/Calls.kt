package com.getcode.solana.rpc

import com.getcode.solana.keys.PublicKey
import com.solana.networking.Rpc20Driver
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import android.util.Base64

/**
 * Returns the most recent blockhash as a base58 string.
 */
suspend fun Rpc20Driver.getLatestBlockhash(): Result<String> {
    val response = runCatching {
        makeRequest(
            request = GetLatestBlockhash(),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
    response.error?.let { return Result.failure(RpcException(it.code, it.message)) }

    val blockhash = response.result
        ?.jsonObject?.get("value")
        ?.jsonObject?.get("blockhash")
        ?.jsonPrimitive?.content
        ?: return Result.failure(Throwable("Missing blockhash"))

    return Result.success(blockhash)
}

/**
 * Returns the SOL balance (in lamports) for the given public key.
 */
suspend fun Rpc20Driver.getBalance(publicKey: PublicKey): Result<Long> {
    val response = runCatching {
        makeRequest(
            request = GetBalance(publicKey),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
    val error = response.error
    if (error != null) {
        return Result.failure(RpcException(error.code, error.message))
    }

    val lamports = response.result
        ?.jsonObject?.get("value")
        ?.jsonPrimitive?.long
        ?: return Result.failure(Throwable("Missing balance value"))

    return Result.success(lamports)
}

/**
 * Returns the token balance (raw amount) for the given token account.
 * Returns 0 if the account does not exist.
 */
suspend fun Rpc20Driver.getTokenAccountBalance(tokenAccount: PublicKey): Result<Long> {
    val response = runCatching {
        makeRequest(
            request = GetTokenAccountBalance(tokenAccount),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
    val error = response.error
    if (error != null) {
        // Account not found — treat as zero balance
        if (error.code == -32602 || error.code == -32600) {
            return Result.success(0L)
        }
        return Result.failure(RpcException(error.code, error.message))
    }

    val amount = response.result
        ?.jsonObject?.get("value")
        ?.jsonObject?.get("amount")
        ?.jsonPrimitive?.content
        ?.toLongOrNull()
        ?: return Result.success(0L)

    return Result.success(amount)
}

/**
 * Checks if an account exists on the blockchain.
 *
 * This function queries the Solana RPC endpoint to determine if an account
 * associated with the provided public key has been initialized on the network.
 *
 * @param publicKey The public key of the account to check.
 * @return A [Result] which is [Result.success] with [Unit] if the account exists,
 * or [Result.failure] with an [RpcException] or other [Throwable] if an error occurs
 * (e.g., network issue, account not found, or RPC error).
 */
suspend fun Rpc20Driver.doesAccountExist(publicKey: PublicKey): Result<Unit> {
    val response = runCatching {
        makeRequest(
            request = GetAccountInfo(publicKey),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
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

/**
 * Returns the raw account data for the given public key, base64-decoded.
 */
suspend fun Rpc20Driver.getAccountData(publicKey: PublicKey): Result<ByteArray> {
    val response = runCatching {
        makeRequest(
            request = GetAccountInfo(publicKey),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
    val error = response.error
    if (error != null) {
        return Result.failure(RpcException(error.code, error.message))
    }

    val value = response.result?.jsonObject?.get("value")?.takeIf { it !is JsonNull }
        ?: return Result.failure(Throwable("Account not found"))

    val dataArray = value.jsonObject["data"]?.jsonArray
        ?: return Result.failure(Throwable("Missing account data"))

    val base64String = dataArray[0].jsonPrimitive.content
    val decoded = Base64.decode(base64String, Base64.NO_WRAP)
    return Result.success(decoded)
}

/**
 * Sends a transaction to the Solana blockchain.
 *
 * @param encodedTransaction The base58 encoded transaction string.
 * @return A [Result] object containing the transaction signature as a [String] on success,
 * or an [RpcException] on failure.
 */
suspend fun Rpc20Driver.sendTransaction(encodedTransaction: String): Result<String> {
    println("sending transaction on the blockchain => $encodedTransaction")
    val response = runCatching {
        makeRequest(
            request = SendTransaction(encodedTransaction),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
    val error = response.error
    if (error != null) {
        return Result.failure(RpcException(error.code, error.message))
    }

    val signature = response.result
        ?: return Result.failure(Throwable("Missing signature"))

    return Result.success(signature.toString())
}

/**
 * Simulates a transaction on the Solana blockchain.
 *
 * This function sends a transaction simulation request to the Solana RPC endpoint.
 * It allows you to see the potential outcome of a transaction, including any errors
 * and logs, without actually committing it to the blockchain.
 *
 * @param encodedTransaction The base58 encoded transaction string to simulate.
 * @return A [Result] object which is [Result.success] with the simulated transaction's
 * "signature" (often null or a placeholder in simulation, the actual value depends on the RPC node's response structure for simulations)
 * as a [String] if the simulation is successful.
 * If the simulation encounters an error, it returns [Result.failure] with an [RpcException]
 * containing the error code, message, and potentially additional details like `err` and `logs`
 * from the RPC response. It can also return [Result.failure] with a generic [Throwable]
 * if essential parts of the response (like the result or signature field) are missing.
 */
suspend fun Rpc20Driver.simulateTransaction(
    encodedTransaction: String,
    commitment: String = "confirmed",
): Result<String> {
    println("simulating transaction on the blockchain => $encodedTransaction")
    val response = runCatching {
        makeRequest(
            request = SimulateTransaction(encodedTransaction, commitment),
            resultSerializer = JsonElement.serializer()
        )
    }.getOrElse { return Result.failure(it) }
    val error = response.error
    val errorDetails = response.result?.jsonObject?.get("err")?.toString()
    if (error != null || errorDetails != null) {
        val logs = response.result?.jsonObject?.get("logs")?.toString() ?: "No logs available"
        return Result.failure(
            RpcException(
                error?.code ?: -1,
                "${error?.message}, err: $errorDetails, logs: $logs"
            )
        )
    }

    val signature = response.result
        ?: return Result.failure(Throwable("Failed to simulate"))

    return Result.success(signature.toString())
}

class RpcException(
    val code: Int,
    override val message: String,
) : Throwable(message) {
    val isBlockhashNotFound: Boolean
        get() = message.contains("Blockhash not found", ignoreCase = true) ||
                message.contains("BlockhashNotFound", ignoreCase = true)
}