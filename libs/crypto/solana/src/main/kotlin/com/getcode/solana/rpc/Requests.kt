package com.getcode.solana.rpc

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.solana.rpccore.JsonRpc20Request
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

internal class GetAccountInfo(
    publicKey: PublicKey,
    encoding: String = "base64",
    commitment: String = "confirmed",
    requestId: String = "1",
): JsonRpc20Request(
    method = "getAccountInfo",
    params = buildJsonArray {
        add(publicKey.base58())
        addJsonObject {
            put("commitment", commitment)
            put("encoding", encoding)
        }
    },
    id = requestId
)

internal class SendTransaction(
    val encodedTransaction: String,
    val requestId: String = "1",
): JsonRpc20Request(
    method = "sendTransaction",
    params = buildJsonArray { add(encodedTransaction) },
    id = requestId,
)

internal class SimulateTransaction(
    encodedTransaction: String,
    commitment: String = "finalized",
    requestId: String = "1",
): JsonRpc20Request(
    method = "simulateTransaction",
    params = buildJsonArray {
        add(encodedTransaction)
        addJsonObject {
            put("commitment", commitment)
            put("encoding", "base64")
        }
    },
    id = requestId
)