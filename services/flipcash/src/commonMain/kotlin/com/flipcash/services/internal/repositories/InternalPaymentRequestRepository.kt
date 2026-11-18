package com.flipcash.services.internal.repositories

import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.internal.network.services.PaymentRequestService
import com.flipcash.services.models.CreatePaymentRequestError
import com.flipcash.services.models.GetChatPaymentRequestsError
import com.flipcash.services.models.GetPayersError
import com.flipcash.services.models.GetPaymentIntentIdError
import com.flipcash.services.models.GetPaymentRequestError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.NewPaymentRequest
import com.flipcash.services.models.paymentrequest.PayerPage
import com.flipcash.services.models.paymentrequest.PayerSection
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.flipcash.services.repository.PaymentRequestRepository
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils

internal class InternalPaymentRequestRepository(
    private val service: PaymentRequestService,
) : PaymentRequestRepository {
    override suspend fun createPaymentRequest(
        owner: KeyPair,
        request: NewPaymentRequest,
        idempotencyKey: IdempotencyKey,
    ): Result<PaymentRequest> {
        return service.createPaymentRequest(owner, request, idempotencyKey)
            .onFailure {
                if (it !is CreatePaymentRequestError.Denied && it !is CreatePaymentRequestError.TextModerated) {
                    ErrorUtils.handleError(it)
                }
            }
    }

    override suspend fun getPaymentRequest(
        owner: KeyPair,
        paymentRequestId: ID,
    ): Result<PaymentRequestDetails> {
        return service.getPaymentRequest(owner, paymentRequestId)
            .onFailure { if (it !is GetPaymentRequestError.NotFound) ErrorUtils.handleError(it) }
    }

    override suspend fun getChatPaymentRequests(
        owner: KeyPair,
        chatId: ChatId,
        section: PaymentRequestSection,
        queryOptions: QueryOptions,
    ): Result<PaymentRequestPage> {
        return service.getChatPaymentRequests(owner, chatId, section, queryOptions)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getPaymentIntentId(
        owner: KeyPair,
        paymentRequestId: ID,
    ): Result<PaymentIntentId> {
        // NotOpen, NotAPayer and AlreadyPaid answer a race the user can see (the request closed,
        // or they paid elsewhere), not a fault.
        return service.getPaymentIntentId(owner, paymentRequestId)
            .onFailure {
                if (it !is GetPaymentIntentIdError.NotFound &&
                    it !is GetPaymentIntentIdError.NotOpen &&
                    it !is GetPaymentIntentIdError.NotAPayer &&
                    it !is GetPaymentIntentIdError.AlreadyPaid
                ) {
                    ErrorUtils.handleError(it)
                }
            }
    }

    override suspend fun getPayers(
        owner: KeyPair,
        paymentRequestId: ID,
        section: PayerSection,
        queryOptions: QueryOptions,
    ): Result<PayerPage> {
        return service.getPayers(owner, paymentRequestId, section, queryOptions)
            .onFailure { if (it !is GetPayersError.NotFound) ErrorUtils.handleError(it) }
    }

    override suspend fun closePaymentRequest(
        owner: KeyPair,
        paymentRequestId: ID,
    ): Result<PaymentRequest> {
        return service.closePaymentRequest(owner, paymentRequestId)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun markPaid(
        owner: KeyPair,
        paymentRequestId: ID,
        userId: ID,
    ): Result<PaymentRequest> {
        return service.markPaid(owner, paymentRequestId, userId)
            .onFailure { ErrorUtils.handleError(it) }
    }
}
