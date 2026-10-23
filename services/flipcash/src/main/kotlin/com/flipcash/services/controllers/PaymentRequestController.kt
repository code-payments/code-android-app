package com.flipcash.services.controllers

import com.codeinc.flipcash.gen.paymentrequest.v1.Model
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.flipcash.services.repository.PaymentRequestRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

class PaymentRequestController @Inject constructor(
    private val repository: PaymentRequestRepository,
    private val userManager: UserManager,
) {
    suspend fun createPaymentRequest(groupChat: Model.GroupChatPaymentRequest, idempotencyKey: Model.IdempotencyKey): Result<Model.PaymentRequestMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.createPaymentRequest(owner, groupChat, idempotencyKey)
    }

    suspend fun getPaymentRequest(paymentRequestId: Model.PaymentRequestId): Result<PaymentRequestDetails> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.getPaymentRequest(owner, paymentRequestId)
    }

    suspend fun getChatPaymentRequests(chatId: ChatId, section: PaymentRequestSection, queryOptions: QueryOptions = QueryOptions()): Result<PaymentRequestPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.getChatPaymentRequests(owner, chatId, section, queryOptions)
    }

    suspend fun closePaymentRequest(paymentRequestId: Model.PaymentRequestId): Result<Model.PaymentRequestMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.closePaymentRequest(owner, paymentRequestId)
    }

    suspend fun markPaid(paymentRequestId: Model.PaymentRequestId, userId: ID): Result<Model.PaymentRequestMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.markPaid(owner, paymentRequestId, userId)
    }
}
