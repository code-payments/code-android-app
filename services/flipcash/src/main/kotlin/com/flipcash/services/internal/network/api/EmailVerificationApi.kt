package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.email.v1.EmailVerificationGrpcKt
import com.codeinc.flipcash.gen.email.v1.EmailVerificationService
import com.codeinc.flipcash.gen.email.v1.Model
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.ContactMethod
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import com.codeinc.flipcash.gen.email.v1.validate
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EmailVerificationApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = EmailVerificationGrpcKt.EmailVerificationCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Sends a verification code to the provided email address.
     * If an active verification is already taking place, the existing code will be
     * resent.
     */
    suspend fun sendVerificationCode(
        request: ContactMethod.Email,
        owner: Ed25519.KeyPair
    ): EmailVerificationService.SendVerificationCodeResponse {
        val request = EmailVerificationService.SendVerificationCodeRequest.newBuilder()
            .setEmailAddress(Model.EmailAddress.newBuilder().setValue(request.emailAddress).build())
            .setClientData(request.clientData)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.sendVerificationCode(request)
        }
    }

    /**
     * Validates a verification code. On success, the email address is linked to the user. Any previous links are overwritten.
     */
    suspend fun checkVerificationCode(
        request: ContactMethod.Email,
        code: String,
        owner: Ed25519.KeyPair
    ): EmailVerificationService.CheckVerificationCodeResponse {
        val request = EmailVerificationService.CheckVerificationCodeRequest.newBuilder()
            .setEmailAddress(Model.EmailAddress.newBuilder().setValue(request.emailAddress).build())
            .setCode(Model.VerificationCode.newBuilder().setValue(code).build())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.checkVerificationCode(request)
        }
    }

    suspend fun unlink(
        request: ContactMethod.Email,
        owner: Ed25519.KeyPair
    ): EmailVerificationService.UnlinkResponse {
        val request = EmailVerificationService.UnlinkRequest.newBuilder()
            .setEmailAddress(Model.EmailAddress.newBuilder().setValue(request.emailAddress).build())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.unlink(request)
        }
    }
}