package com.flipcash.services.repository

import com.flipcash.services.models.chat.IdempotencyKey
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
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID

/** Payment requests in group chats. */
interface PaymentRequestRepository {
    suspend fun createPaymentRequest(
        owner: KeyPair,
        request: NewPaymentRequest,
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

    /**
     * The intent id the caller's payment into the request must use. Ask when the user starts to
     * pay, and keep it to that payment: anyone who learns it first can use it up.
     */
    suspend fun getPaymentIntentId(
        owner: KeyPair,
        paymentRequestId: ID,
    ): Result<PaymentIntentId>

    /** One page of the request's payers in [section]; page with [PayerPage.pagingToken]. */
    suspend fun getPayers(
        owner: KeyPair,
        paymentRequestId: ID,
        section: PayerSection,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<PayerPage>

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
