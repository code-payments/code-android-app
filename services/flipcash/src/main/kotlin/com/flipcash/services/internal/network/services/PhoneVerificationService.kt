package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.PhoneVerificationApi
import com.flipcash.services.models.PhoneVerificationError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationService as RpcPhoneService


internal class PhoneVerificationService @Inject constructor(
    private val api: PhoneVerificationApi,
) {
    suspend fun sendVerificationCode(
        phoneNumber: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.sendVerificationCode(phoneNumber, owner)
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
                Result.failure(PhoneVerificationError.Other(cause))

            }
        )
    }

    suspend fun checkVerificationCode(
        phoneNumber: String,
        code: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.checkVerificationCode(phoneNumber, code, owner)
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
                Result.failure(PhoneVerificationError.Other(cause))
            }
        )
    }
}