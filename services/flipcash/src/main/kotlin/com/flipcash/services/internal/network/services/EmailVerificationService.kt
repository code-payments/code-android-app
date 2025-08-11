package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.EmailVerificationApi
import com.flipcash.services.models.EmailVerificationError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject
import com.codeinc.flipcash.gen.email.v1.EmailVerificationService as RpcEmailService


internal class EmailVerificationService @Inject constructor(
    private val api: EmailVerificationApi,
) {
    suspend fun sendVerificationCode(
        emailAddress: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.sendVerificationCode(emailAddress, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcEmailService.SendVerificationCodeResponse.Result.OK -> Result.success(Unit)
                    RpcEmailService.SendVerificationCodeResponse.Result.DENIED -> {
                        Result.failure(EmailVerificationError.Denied())
                    }
                    RpcEmailService.SendVerificationCodeResponse.Result.RATE_LIMITED -> {
                        Result.failure(EmailVerificationError.RateLimited())
                    }
                    RpcEmailService.SendVerificationCodeResponse.Result.INVALID_EMAIL_ADDRESS -> {
                        Result.failure(EmailVerificationError.InvalidEmailAddress())
                    }
                    RpcEmailService.SendVerificationCodeResponse.Result.UNRECOGNIZED -> {
                        Result.failure(EmailVerificationError.Unrecognized())
                    }
                    else -> Result.failure(EmailVerificationError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(EmailVerificationError.Other(cause))

            }
        )
    }

    suspend fun checkVerificationCode(
        emailAddress: String,
        code: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.checkVerificationCode(emailAddress, code, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcEmailService.CheckVerificationCodeResponse.Result.OK -> Result.success(Unit)
                    RpcEmailService.CheckVerificationCodeResponse.Result.DENIED -> {
                        Result.failure(EmailVerificationError.Denied())
                    }
                    RpcEmailService.CheckVerificationCodeResponse.Result.RATE_LIMITED -> {
                        Result.failure(EmailVerificationError.RateLimited())
                    }
                    RpcEmailService.CheckVerificationCodeResponse.Result.INVALID_CODE -> {
                        Result.failure(EmailVerificationError.InvalidVerificationCode())
                    }
                    RpcEmailService.CheckVerificationCodeResponse.Result.NO_VERIFICATION -> {
                        Result.failure(EmailVerificationError.NoVerification())
                    }
                    RpcEmailService.CheckVerificationCodeResponse.Result.UNRECOGNIZED -> {
                        Result.failure(EmailVerificationError.Unrecognized())
                    }
                    else -> Result.failure(EmailVerificationError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(EmailVerificationError.Other(cause))
            }
        )
    }
}