package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.paymentrequest.v1.Model
import com.codeinc.flipcash.gen.paymentrequest.v1.PaymentRequestGrpcKt
import com.codeinc.flipcash.gen.paymentrequest.v1.PaymentrequestService
import com.codeinc.flipcash.gen.paymentrequest.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.asUserId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.model.core.ID
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PaymentRequestApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = PaymentRequestGrpcKt.PaymentRequestCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun createPaymentRequest(
        owner: Ed25519.KeyPair,
        groupChat: Model.GroupChatPaymentRequest,
        idempotencyKey: Model.IdempotencyKey,
    ): PaymentrequestService.CreatePaymentRequestResponse {
        val request = PaymentrequestService.CreatePaymentRequestRequest.newBuilder()
            .setIdempotencyKey(idempotencyKey)
            .setGroupChat(groupChat)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.createPaymentRequest(request)
        }
    }

    suspend fun getPaymentRequest(
        owner: Ed25519.KeyPair,
        paymentRequestId: Model.PaymentRequestId,
    ): PaymentrequestService.GetPaymentRequestResponse {
        val request = PaymentrequestService.GetPaymentRequestRequest.newBuilder()
            .setPaymentRequestId(paymentRequestId)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getPaymentRequest(request)
        }
    }

    suspend fun getChatPaymentRequests(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        section: PaymentRequestSection,
        queryOptions: QueryOptions,
    ): PaymentrequestService.GetChatPaymentRequestsResponse {
        val request = PaymentrequestService.GetChatPaymentRequestsRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setSection(
                when (section) {
                    PaymentRequestSection.OPEN ->
                        PaymentrequestService.GetChatPaymentRequestsRequest.Section.OPEN
                    PaymentRequestSection.RESOLVED ->
                        PaymentrequestService.GetChatPaymentRequestsRequest.Section.RESOLVED
                }
            )
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getChatPaymentRequests(request)
        }
    }

    suspend fun closePaymentRequest(
        owner: Ed25519.KeyPair,
        paymentRequestId: Model.PaymentRequestId,
    ): PaymentrequestService.ClosePaymentRequestResponse {
        val request = PaymentrequestService.ClosePaymentRequestRequest.newBuilder()
            .setPaymentRequestId(paymentRequestId)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.closePaymentRequest(request)
        }
    }

    suspend fun markPaid(
        owner: Ed25519.KeyPair,
        paymentRequestId: Model.PaymentRequestId,
        userId: ID,
    ): PaymentrequestService.MarkPaidResponse {
        val request = PaymentrequestService.MarkPaidRequest.newBuilder()
            .setPaymentRequestId(paymentRequestId)
            .setUserId(userId.asUserId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.markPaid(request)
        }
    }
}
