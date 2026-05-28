package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.phone.v1.Model
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationGrpcKt
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.ContactMethod
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.codeinc.flipcash.gen.phone.v1.validate
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PhoneVerificationApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = PhoneVerificationGrpcKt.PhoneVerificationCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Sends a verification code to the provided phone number.
     * If an active verification is already taking place, the existing code will be
     * resent.
     */
    suspend fun sendVerificationCode(
        request: ContactMethod.Phone,
        owner: Ed25519.KeyPair
    ): PhoneVerificationService.SendVerificationCodeResponse {
        val request = PhoneVerificationService.SendVerificationCodeRequest.newBuilder()
            .setPhoneNumber(Model.PhoneNumber.newBuilder().setValue(request.phoneNumber).build())
            .setPlatform(Common.Platform.GOOGLE)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.sendVerificationCode(request)
        }
    }

    /**
     * Validates a verification code. On success, the phone number is linked to the user. Any previous links are overwritten.
     */
    suspend fun checkVerificationCode(
        request: ContactMethod.Phone,
        code: String,
        owner: Ed25519.KeyPair
    ): PhoneVerificationService.CheckVerificationCodeResponse {
        val request = PhoneVerificationService.CheckVerificationCodeRequest.newBuilder()
            .setPhoneNumber(Model.PhoneNumber.newBuilder().setValue(request.phoneNumber).build())
            .setCode(Model.VerificationCode.newBuilder().setValue(code).build())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.checkVerificationCode(request)
        }
    }

    /**
     * Removes the link of an phone number from a user.
     */
    suspend fun unlink(
        request: ContactMethod.Phone,
        owner: Ed25519.KeyPair
    ): PhoneVerificationService.UnlinkResponse {
        val request = PhoneVerificationService.UnlinkRequest.newBuilder()
            .setPhoneNumber(Model.PhoneNumber.newBuilder().setValue(request.phoneNumber).build())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unlink(request)
        }
    }

    suspend fun linkForPayment(
        request: ContactMethod.Phone,
        owner: Ed25519.KeyPair
    ): PhoneVerificationService.LinkForPaymentResponse {
        val request = PhoneVerificationService.LinkForPaymentRequest.newBuilder()
            .setPhoneNumber(Model.PhoneNumber.newBuilder().setValue(request.phoneNumber).build())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.linkForPayment(request)
        }
    }
}