package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.paymentrequest.v1.Model
import com.codeinc.flipcash.gen.paymentrequest.v1.PaymentrequestService
import com.flipcash.services.internal.network.api.PaymentRequestApi
import com.flipcash.services.internal.network.extensions.toFlaggedCategory
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.models.ClosePaymentRequestError
import com.flipcash.services.models.CreatePaymentRequestError
import com.flipcash.services.models.GetChatPaymentRequestsError
import com.flipcash.services.models.GetPaymentRequestError
import com.flipcash.services.models.MarkPaymentRequestPaidError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject

internal class PaymentRequestService @Inject constructor(
    private val api: PaymentRequestApi,
) {
    suspend fun createPaymentRequest(
        owner: Ed25519.KeyPair,
        groupChat: Model.GroupChatPaymentRequest,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PaymentRequestMetadata> {
        return runCatching {
            api.createPaymentRequest(owner, groupChat, idempotencyKey)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PaymentrequestService.CreatePaymentRequestResponse.Result.OK -> Result.success(response.paymentRequest)
                    PaymentrequestService.CreatePaymentRequestResponse.Result.DENIED -> Result.failure(CreatePaymentRequestError.Denied())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.CHAT_NOT_SUPPORTED -> Result.failure(CreatePaymentRequestError.ChatNotSupported())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.ENCRYPTION_NOT_ALLOWED -> Result.failure(CreatePaymentRequestError.EncryptionNotAllowed())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.ENCRYPTION_REQUIRED -> Result.failure(CreatePaymentRequestError.EncryptionRequired())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.INVALID_ENDS_AT -> Result.failure(CreatePaymentRequestError.InvalidEndsAt())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.INVALID_SHARES -> Result.failure(CreatePaymentRequestError.InvalidShares())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.INVALID_CURRENCY -> Result.failure(CreatePaymentRequestError.InvalidCurrency())
                    PaymentrequestService.CreatePaymentRequestResponse.Result.TEXT_MODERATED -> Result.failure(CreatePaymentRequestError.TextModerated(response.flaggedCategory.toFlaggedCategory()))
                    PaymentrequestService.CreatePaymentRequestResponse.Result.UNRECOGNIZED -> Result.failure(CreatePaymentRequestError.Unrecognized())
                    else -> Result.failure(CreatePaymentRequestError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { CreatePaymentRequestError.Other(cause = it) })
            }
        )
    }

    suspend fun getPaymentRequest(
        owner: Ed25519.KeyPair,
        paymentRequestId: Model.PaymentRequestId,
    ): Result<PaymentRequestDetails> {
        return runCatching {
            api.getPaymentRequest(owner, paymentRequestId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PaymentrequestService.GetPaymentRequestResponse.Result.OK -> Result.success(PaymentRequestDetails(
                        paymentRequest = response.paymentRequest,
                        paymentIntentId = if (response.hasPaymentIntentId()) response.paymentIntentId else null,
                    ))
                    PaymentrequestService.GetPaymentRequestResponse.Result.DENIED -> Result.failure(GetPaymentRequestError.Denied())
                    PaymentrequestService.GetPaymentRequestResponse.Result.NOT_FOUND -> Result.failure(GetPaymentRequestError.NotFound())
                    PaymentrequestService.GetPaymentRequestResponse.Result.UNRECOGNIZED -> Result.failure(GetPaymentRequestError.Unrecognized())
                    else -> Result.failure(GetPaymentRequestError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetPaymentRequestError.Other(cause = it) })
            }
        )
    }

    suspend fun getChatPaymentRequests(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        section: PaymentRequestSection,
        queryOptions: QueryOptions,
    ): Result<PaymentRequestPage> {
        return runCatching {
            api.getChatPaymentRequests(owner, chatId, section, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PaymentrequestService.GetChatPaymentRequestsResponse.Result.OK -> Result.success(PaymentRequestPage(
                        paymentRequests = response.paymentRequestsList,
                        pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                        hasMore = response.hasMore,
                    ))
                    PaymentrequestService.GetChatPaymentRequestsResponse.Result.DENIED -> Result.failure(GetChatPaymentRequestsError.Denied())
                    PaymentrequestService.GetChatPaymentRequestsResponse.Result.NOT_FOUND -> Result.failure(GetChatPaymentRequestsError.NotFound())
                    PaymentrequestService.GetChatPaymentRequestsResponse.Result.UNRECOGNIZED -> Result.failure(GetChatPaymentRequestsError.Unrecognized())
                    else -> Result.failure(GetChatPaymentRequestsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetChatPaymentRequestsError.Other(cause = it) })
            }
        )
    }

    suspend fun closePaymentRequest(
        owner: Ed25519.KeyPair,
        paymentRequestId: Model.PaymentRequestId,
    ): Result<Model.PaymentRequestMetadata> {
        return runCatching {
            api.closePaymentRequest(owner, paymentRequestId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PaymentrequestService.ClosePaymentRequestResponse.Result.OK -> Result.success(response.paymentRequest)
                    PaymentrequestService.ClosePaymentRequestResponse.Result.DENIED -> Result.failure(ClosePaymentRequestError.Denied())
                    PaymentrequestService.ClosePaymentRequestResponse.Result.NOT_FOUND -> Result.failure(ClosePaymentRequestError.NotFound())
                    PaymentrequestService.ClosePaymentRequestResponse.Result.UNRECOGNIZED -> Result.failure(ClosePaymentRequestError.Unrecognized())
                    else -> Result.failure(ClosePaymentRequestError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { ClosePaymentRequestError.Other(cause = it) })
            }
        )
    }

    suspend fun markPaid(
        owner: Ed25519.KeyPair,
        paymentRequestId: Model.PaymentRequestId,
        userId: ID,
    ): Result<Model.PaymentRequestMetadata> {
        return runCatching {
            api.markPaid(owner, paymentRequestId, userId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PaymentrequestService.MarkPaidResponse.Result.OK -> Result.success(response.paymentRequest)
                    PaymentrequestService.MarkPaidResponse.Result.DENIED -> Result.failure(MarkPaymentRequestPaidError.Denied())
                    PaymentrequestService.MarkPaidResponse.Result.NOT_FOUND -> Result.failure(MarkPaymentRequestPaidError.NotFound())
                    PaymentrequestService.MarkPaidResponse.Result.NOT_OPEN -> Result.failure(MarkPaymentRequestPaidError.NotOpen())
                    PaymentrequestService.MarkPaidResponse.Result.NOT_A_SHARE -> Result.failure(MarkPaymentRequestPaidError.NotAShare())
                    PaymentrequestService.MarkPaidResponse.Result.UNRECOGNIZED -> Result.failure(MarkPaymentRequestPaidError.Unrecognized())
                    else -> Result.failure(MarkPaymentRequestPaidError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { MarkPaymentRequestPaidError.Other(cause = it) })
            }
        )
    }
}
