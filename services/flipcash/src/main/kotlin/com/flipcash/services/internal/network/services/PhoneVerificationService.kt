package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.PhoneVerificationApi
import com.getcode.opencode.utils.toValidationOrElse
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.LinkForPaymentError
import com.flipcash.services.models.PhoneVerificationError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationService as RpcPhoneService


internal class PhoneVerificationService @Inject constructor(
    private val api: PhoneVerificationApi,
) {
    suspend fun sendVerificationCode(
        request: ContactMethod.Phone,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.sendVerificationCode(request, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcPhoneService.SendVerificationCodeResponse.Result.OK -> Result.success(Unit)
                    RpcPhoneService.SendVerificationCodeResponse.Result.DENIED -> {
                        Result.failure(PhoneVerificationError.Denied())
                    }
                    RpcPhoneService.SendVerificationCodeResponse.Result.RATE_LIMITED -> {
                        Result.failure(PhoneVerificationError.RateLimited())
                    }
                    RpcPhoneService.SendVerificationCodeResponse.Result.INVALID_PHONE_NUMBER -> {
                        Result.failure(PhoneVerificationError.InvalidPhoneNumber())
                    }
                    RpcPhoneService.SendVerificationCodeResponse.Result.UNSUPPORTED_PHONE_TYPE -> {
                        Result.failure(PhoneVerificationError.UnsupportedPhoneType())
                    }
                    RpcPhoneService.SendVerificationCodeResponse.Result.UNRECOGNIZED -> {
                        Result.failure(PhoneVerificationError.Unrecognized())
                    }
                    else -> Result.failure(PhoneVerificationError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { PhoneVerificationError.Other(it) })

            }
        )
    }

    suspend fun checkVerificationCode(
        request: ContactMethod.Phone,
        code: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.checkVerificationCode(request, code, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcPhoneService.CheckVerificationCodeResponse.Result.OK -> Result.success(Unit)
                    RpcPhoneService.CheckVerificationCodeResponse.Result.DENIED -> {
                        Result.failure(PhoneVerificationError.Denied())
                    }
                    RpcPhoneService.CheckVerificationCodeResponse.Result.RATE_LIMITED -> {
                        Result.failure(PhoneVerificationError.RateLimited())
                    }
                    RpcPhoneService.CheckVerificationCodeResponse.Result.INVALID_CODE -> {
                        Result.failure(PhoneVerificationError.InvalidVerificationCode())
                    }
                    RpcPhoneService.CheckVerificationCodeResponse.Result.NO_VERIFICATION -> {
                        Result.failure(PhoneVerificationError.NoVerification())
                    }
                    RpcPhoneService.CheckVerificationCodeResponse.Result.UNRECOGNIZED -> {
                        Result.failure(PhoneVerificationError.Unrecognized())
                    }
                    else -> Result.failure(PhoneVerificationError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { PhoneVerificationError.Other(it) })
            }
        )
    }

    suspend fun unlink(
        request: ContactMethod.Phone,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.unlink(request, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcPhoneService.UnlinkResponse.Result.OK -> Result.success(Unit)
                    RpcPhoneService.UnlinkResponse.Result.DENIED -> {
                        Result.failure(PhoneVerificationError.Denied())
                    }

                    RpcPhoneService.UnlinkResponse.Result.UNRECOGNIZED -> {
                        Result.failure(PhoneVerificationError.Unrecognized())
                    }

                    else -> Result.failure(PhoneVerificationError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { PhoneVerificationError.Other(it) })
            }
        )
    }

    suspend fun linkForPayment(
        request: ContactMethod.Phone,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.linkForPayment(request, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcPhoneService.LinkForPaymentResponse.Result.OK -> Result.success(Unit)
                    RpcPhoneService.LinkForPaymentResponse.Result.DENIED -> {
                        Result.failure(LinkForPaymentError.Denied())
                    }
                    RpcPhoneService.LinkForPaymentResponse.Result.NOT_ASSOCIATED -> {
                        Result.failure(LinkForPaymentError.NotAssociated())
                    }
                    RpcPhoneService.LinkForPaymentResponse.Result.UNRECOGNIZED -> {
                        Result.failure(LinkForPaymentError.Unrecognized())
                    }
                    else -> Result.failure(LinkForPaymentError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { LinkForPaymentError.Other(it) })
            }
        )
    }
}