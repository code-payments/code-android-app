package com.getcode.network.api

import com.codeinc.gen.transaction.v2.TransactionGrpc
import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.SwapRequest
import com.codeinc.gen.transaction.v2.TransactionService.SwapResponse
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class TransactionApiV2 @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = TransactionGrpc.newStub(managedChannel).withWaitForReady()

    fun submitIntent(request: StreamObserver<TransactionService.SubmitIntentResponse>): StreamObserver<TransactionService.SubmitIntentRequest> {
        return api.submitIntent(request)
    }

    fun airdrop(request: TransactionService.AirdropRequest): Single<TransactionService.AirdropResponse> {
        return api::airdrop
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getPrivacyUpgradeStatus(request: TransactionService.GetPrivacyUpgradeStatusRequest): Single<TransactionService.GetPrivacyUpgradeStatusResponse> {
        return api::getPrivacyUpgradeStatus
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getPaymentHistory(request: TransactionService.GetPaymentHistoryRequest): Single<TransactionService.GetPaymentHistoryResponse> {
        return api::getPaymentHistory
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getLimits(request: TransactionService.GetLimitsRequest): Single<TransactionService.GetLimitsResponse> {
        return api::getLimits
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getIntentMetadata(request: TransactionService.GetIntentMetadataRequest): Single<TransactionService.GetIntentMetadataResponse> {
        return api::getIntentMetadata
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun canWithdrawToAccount(request: TransactionService.CanWithdrawToAccountRequest): Single<TransactionService.CanWithdrawToAccountResponse> {
        return api::canWithdrawToAccount
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getPrioritizedIntentsForPrivacyUpgrade(request: TransactionService.GetPrioritizedIntentsForPrivacyUpgradeRequest): Single<TransactionService.GetPrioritizedIntentsForPrivacyUpgradeResponse> {
        return api::getPrioritizedIntentsForPrivacyUpgrade
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun swap(observer: StreamObserver<SwapResponse>): StreamObserver<SwapRequest> {
        return api.swap(observer)
    }

    fun declareFiatPurchase(request: TransactionService.DeclareFiatOnrampPurchaseAttemptRequest): Flow<TransactionService.DeclareFiatOnrampPurchaseAttemptResponse> {
        return api::declareFiatOnrampPurchaseAttempt
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

}
