package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.phone.v1.Model
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationGrpcKt
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class PhoneVerificationApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = PhoneVerificationGrpcKt.PhoneVerificationCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Sends a verification code to the provided email address.
     * If an active verification is already taking place, the existing code will be
     * resent.
     */
    suspend fun sendVerificationCode(
        phoneNumber: String,
        owner: Ed25519.KeyPair
    ): PhoneVerificationService.SendVerificationCodeResponse {
        val request = PhoneVerificationService.SendVerificationCodeRequest.newBuilder()
            .setPhoneNumber(Model.PhoneNumber.newBuilder().setValue(phoneNumber).build())
            .setPlatform(Common.Platform.GOOGLE)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.sendVerificationCode(request)
        }
    }

    /**
     * Validates a verification code. On success, the email address is linked to the user.
     */
    suspend fun checkVerificationCode(
        phoneNumber: String,
        code: String,
        owner: Ed25519.KeyPair
    ): PhoneVerificationService.CheckVerificationCodeResponse {
        val request = PhoneVerificationService.CheckVerificationCodeRequest.newBuilder()
            .setPhoneNumber(Model.PhoneNumber.newBuilder().setValue(phoneNumber).build())
            .setCode(Model.VerificationCode.newBuilder().setValue(code).build())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.checkVerificationCode(request)
        }
    }
}