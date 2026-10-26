package com.flipcash.services.repository

import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.NewSplitRequest
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID

/**
 * Payment requests in group chats. The server sends no events for
 * payment requests, so callers refetch.
 */
interface PaymentRequestRepository {
    suspend fun createPaymentRequest(
        owner: KeyPair,
        request: NewSplitRequest,
        idempotencyKey: IdempotencyKey,
    ): Result<PaymentRequest>

    suspend fun getPaymentRequest(
        owner: KeyPair,
        paymentRequestId: ID,
    ): Result<PaymentRequestDetails>

    suspend fun getChatPaymentRequests(
        owner: KeyPair,
        chatId: ChatId,
        section: PaymentRequestSection,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<PaymentRequestPage>

    /** Closes the request. Creator only. */
    suspend fun closePaymentRequest(
        owner: KeyPair,
        paymentRequestId: ID,
    ): Result<PaymentRequest>

    /** Marks [userId]'s share paid outside the app. Creator only. */
    suspend fun markPaid(
        owner: KeyPair,
        paymentRequestId: ID,
        userId: ID,
    ): Result<PaymentRequest>
}
