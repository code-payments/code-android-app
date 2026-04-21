package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.EmailVerificationApi
import com.getcode.opencode.utils.toValidationOrElse
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.EmailVerificationError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject
import com.codeinc.flipcash.gen.email.v1.EmailVerificationService as RpcEmailService


internal class EmailVerificationService @Inject constructor(
    private val api: EmailVerificationApi,
) {
    suspend fun sendVerificationCode(
        request: ContactMethod.Email,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.sendVerificationCode(request, owner)
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
                Result.failure(cause.toValidationOrElse { EmailVerificationError.Other(it) })

            }
        )
    }

    suspend fun checkVerificationCode(
        request: ContactMethod.Email,
        code: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.checkVerificationCode(request, code, owner)
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
                Result.failure(cause.toValidationOrElse { EmailVerificationError.Other(it) })
            }
        )
    }

    suspend fun unlink(
        request: ContactMethod.Email,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.unlink(request, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcEmailService.UnlinkResponse.Result.OK -> Result.success(Unit)
                    RpcEmailService.UnlinkResponse.Result.DENIED -> {
                        Result.failure(EmailVerificationError.Denied())
                    }
                    RpcEmailService.UnlinkResponse.Result.UNRECOGNIZED -> {
                        Result.failure(EmailVerificationError.Unrecognized())
                    }
                    else -> Result.failure(EmailVerificationError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { EmailVerificationError.Other(it) })
            }
        )
    }
}