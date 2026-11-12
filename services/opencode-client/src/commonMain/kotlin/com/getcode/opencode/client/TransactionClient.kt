package com.getcode.opencode.client

import com.getcode.opencode.transport.encodeCanonical
import com.codeinc.opencode.wire.transaction.v1.GetLimitsRequest
import com.codeinc.opencode.wire.transaction.v1.GetLimitsResponse
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.transport.GrpcTransport
import com.squareup.wire.ofEpochSecond
import kotlin.time.Clock
import kotlin.time.Instant

/** `ocp.transaction.v1.Transaction` read calls. SubmitIntent is encoded by [SubmitIntentCodec]. */
class TransactionClient(
    private val transport: GrpcTransport,
    private val clock: Clock = Clock.System,
) {

    /** The owner's send limits, counting what was sent since [consumedSince]. */
    suspend fun getLimits(owner: Ed25519.KeyPair, consumedSince: Instant): Limits {
        val request = LimitsRequests.request(owner, consumedSince.toEpochMilliseconds())
        val response = GetLimitsResponse.ADAPTER.decode(transport.unary(GET_LIMITS, request.encodeCanonical()))
        return LimitsRequests.limits(response, consumedSince.toEpochMilliseconds(), clock.now().toEpochMilliseconds())
    }

    internal companion object {
        const val GET_LIMITS = "/ocp.transaction.v1.Transaction/GetLimits"
    }
}

/** Thrown when GetLimits answers with a result other than OK. */
class GetLimitsException(val result: String) : Exception("GetLimits: $result")

internal object LimitsRequests {
    fun request(owner: Ed25519.KeyPair, consumedSinceMillis: Long): GetLimitsRequest {
        val unsigned = GetLimitsRequest(
            owner = owner.accountId(),
            consumed_since = ofEpochSecond(consumedSinceMillis / 1_000, 0),
        )
        return unsigned.copy(signature = unsigned.signedBy(owner))
    }

    fun limits(response: GetLimitsResponse, sinceMillis: Long, fetchMillis: Long): Limits {
        if (response.result != GetLimitsResponse.Result.OK) throw GetLimitsException(response.result.name)
        return Limits.newInstance(
            sinceDate = sinceMillis,
            fetchDate = fetchMillis,
            sendLimits = response.send_limits_by_currency.mapValues { (_, v) ->
                SendLimit(
                    nextTransaction = v.next_transaction.toDouble(),
                    maxPerTransaction = v.max_per_transaction.toDouble(),
                    maxPerDay = v.max_per_day.toDouble(),
                )
            },
            usdTransactedSinceConsumption = response.usd_transacted,
        )
    }
}
