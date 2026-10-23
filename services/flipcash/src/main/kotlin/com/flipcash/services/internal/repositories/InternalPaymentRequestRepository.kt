package com.flipcash.services.internal.repositories

import com.codeinc.flipcash.gen.paymentrequest.v1.Model
import com.flipcash.services.internal.network.services.PaymentRequestService
import com.flipcash.services.models.CreatePaymentRequestError
import com.flipcash.services.models.GetChatPaymentRequestsError
import com.flipcash.services.models.GetPaymentRequestError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.flipcash.services.repository.PaymentRequestRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils

internal class InternalPaymentRequestRepository(
    private val service: PaymentRequestService,
) : PaymentRequestRepository {
    override suspend fun createPaymentRequest(
        owner: KeyPair,
        groupChat: Model.GroupChatPaymentRequest,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PaymentRequestMetadata> {
        return service.createPaymentRequest(owner, groupChat, idempotencyKey)
            .onFailure {
                if (it !is CreatePaymentRequestError.Denied && it !is CreatePaymentRequestError.TextModerated) {
                    ErrorUtils.handleError(it)
                }
            }
    }

    override suspend fun getPaymentRequest(
        owner: KeyPair,
        paymentRequestId: Model.PaymentRequestId,
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

    override suspend fun closePaymentRequest(
        owner: KeyPair,
        paymentRequestId: Model.PaymentRequestId,
    ): Result<Model.PaymentRequestMetadata> {
        return service.closePaymentRequest(owner, paymentRequestId)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun markPaid(
        owner: KeyPair,
        paymentRequestId: Model.PaymentRequestId,
        userId: ID,
    ): Result<Model.PaymentRequestMetadata> {
        return service.markPaid(owner, paymentRequestId, userId)
            .onFailure { ErrorUtils.handleError(it) }
    }
}
