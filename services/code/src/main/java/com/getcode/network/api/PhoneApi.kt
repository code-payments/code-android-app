package com.getcode.network.api

import com.codeinc.gen.phone.v1.PhoneVerificationGrpc
import com.codeinc.gen.phone.v1.PhoneVerificationService
import com.getcode.annotations.CodeManagedChannel
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class PhoneApi @Inject constructor(
    @CodeManagedChannel
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = PhoneVerificationGrpc.newStub(managedChannel).withWaitForReady()

    fun sendVerificationCode(request: PhoneVerificationService.SendVerificationCodeRequest): @NonNull Single<PhoneVerificationService.SendVerificationCodeResponse> {
        return api::sendVerificationCode
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun checkVerificationCode(request: PhoneVerificationService.CheckVerificationCodeRequest): @NonNull Single<PhoneVerificationService.CheckVerificationCodeResponse> {
        return api::checkVerificationCode
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getAssociatedPhoneNumber(request: PhoneVerificationService.GetAssociatedPhoneNumberRequest): @NonNull Single<PhoneVerificationService.GetAssociatedPhoneNumberResponse> {
        return api::getAssociatedPhoneNumber
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
}
