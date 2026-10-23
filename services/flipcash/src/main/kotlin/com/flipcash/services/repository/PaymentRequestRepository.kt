package com.flipcash.services.repository

import com.codeinc.flipcash.gen.paymentrequest.v1.Model
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID

/**
 * Payment requests in group chats. Request values are the generated `paymentrequest.v1` types
 * for now; a domain model is deferred until a UI consumes them. The server sends no events for
 * payment requests, so callers refetch.
 */
interface PaymentRequestRepository {
    suspend fun createPaymentRequest(
        owner: KeyPair,
        groupChat: Model.GroupChatPaymentRequest,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PaymentRequestMetadata>

    suspend fun getPaymentRequest(
        owner: KeyPair,
        paymentRequestId: Model.PaymentRequestId,
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
        paymentRequestId: Model.PaymentRequestId,
    ): Result<Model.PaymentRequestMetadata>

    /** Marks [userId]'s share paid outside the app. Creator only. */
    suspend fun markPaid(
        owner: KeyPair,
        paymentRequestId: Model.PaymentRequestId,
        userId: ID,
    ): Result<Model.PaymentRequestMetadata>
}
