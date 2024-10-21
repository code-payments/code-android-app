package com.getcode.network.repository

import com.codeinc.gen.phone.v1.PhoneVerificationService
import com.getcode.annotations.CodeNetworkOracle
import com.getcode.db.Database
import com.getcode.ed25519.Ed25519
import com.getcode.network.api.PhoneApi
import com.getcode.network.core.NetworkOracle
import com.getcode.network.integrity.DeviceCheck
import com.getcode.network.integrity.toDeviceToken
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneRepository @Inject constructor(
    private val phoneApi: PhoneApi,
    @CodeNetworkOracle private val networkOracle: NetworkOracle
) {

    var phoneNumber: String = ""
    var phoneLinked: MutableStateFlow<Boolean> = MutableStateFlow(false)

    data class GetAssociatedPhoneNumberResponse(
        val isSuccess: Boolean,
        val isLinked: Boolean,
        val isUnlocked: Boolean,
        val phoneNumber: String
    )

    fun sendVerificationCode(
        phoneValue: String
    ): Flowable<out OtpVerificationResult> {
        if (isMock()) return Single.just(OtpVerificationResult.Success).toFlowable()

        return DeviceCheck.integrityResponseFlowable()
            .flatMap { tokenResult ->
                val request =
                    PhoneVerificationService.SendVerificationCodeRequest.newBuilder()
                        .setPhoneNumber(phoneValue.toPhoneNumber())
                        .apply {
                            if (tokenResult.token != null) {
                                setDeviceToken(tokenResult.token.toDeviceToken())
                            }
                        }.build()


                phoneApi.sendVerificationCode(request)
                    .map { it.result }
                    .let { networkOracle.managedRequest(it) }
                    .map { ret ->
                        when (ret) {
                            PhoneVerificationService.SendVerificationCodeResponse.Result.OK -> OtpVerificationResult.Success
                            PhoneVerificationService.SendVerificationCodeResponse.Result.NOT_INVITED -> OtpVerificationResult.Error.NotInvited
                            PhoneVerificationService.SendVerificationCodeResponse.Result.RATE_LIMITED -> OtpVerificationResult.Error.RateLimited
                            PhoneVerificationService.SendVerificationCodeResponse.Result.INVALID_PHONE_NUMBER -> OtpVerificationResult.Error.InvalidPhoneNumber
                            PhoneVerificationService.SendVerificationCodeResponse.Result.UNSUPPORTED_PHONE_TYPE -> OtpVerificationResult.Error.UnsupportedPhoneType
                            PhoneVerificationService.SendVerificationCodeResponse.Result.UNSUPPORTED_COUNTRY -> OtpVerificationResult.Error.UnsupportedCountry
                            PhoneVerificationService.SendVerificationCodeResponse.Result.UNSUPPORTED_DEVICE -> OtpVerificationResult.Error.UnsupportedDevice
                            PhoneVerificationService.SendVerificationCodeResponse.Result.UNRECOGNIZED -> OtpVerificationResult.Error.Unrecognized
                            else -> OtpVerificationResult.Error.Other
                        }
                    }
            }
    }

    fun checkVerificationCode(
        phoneValue: String,
        otpInput: String
    ): Flowable<CheckVerificationResult> {
        if (isMock()) return Flowable.just(CheckVerificationResult.Success)

        val request = PhoneVerificationService.CheckVerificationCodeRequest.newBuilder()
            .setPhoneNumber(phoneValue.toPhoneNumber())
            .setCode(PhoneVerificationService.VerificationCode.newBuilder().setValue(otpInput))
            .build()

        return phoneApi.checkVerificationCode(request)
            .map { it.result }
            .let { networkOracle.managedRequest(it) }
            .map { result ->
                when (result) {
                    PhoneVerificationService.CheckVerificationCodeResponse.Result.OK -> CheckVerificationResult.Success
                    PhoneVerificationService.CheckVerificationCodeResponse.Result.INVALID_CODE -> CheckVerificationResult.Error.InvalidCode
                    PhoneVerificationService.CheckVerificationCodeResponse.Result.NO_VERIFICATION -> CheckVerificationResult.Error.NoVerification
                    PhoneVerificationService.CheckVerificationCodeResponse.Result.UNRECOGNIZED -> CheckVerificationResult.Error.Unrecognized
                    else -> CheckVerificationResult.Error.Other
                }
            }
    }

    fun fetchAssociatedPhoneNumber(
        keyPair: Ed25519.KeyPair
    ): Flowable<GetAssociatedPhoneNumberResponse> {
        if (isMock()) {
            return Flowable.just(
                GetAssociatedPhoneNumberResponse(true, true, false, "+12223334455")
            )
        }

        val request = PhoneVerificationService.GetAssociatedPhoneNumberRequest.newBuilder()
            .setOwnerAccountId(
                keyPair.publicKeyBytes.toSolanaAccount()
            ).apply { setSignature(sign(keyPair)) }
            .build()

        return phoneApi.getAssociatedPhoneNumber(request)
            .let { networkOracle.managedRequest(it) }
            .map { phone ->
                val isSuccess =
                    phone.result == PhoneVerificationService.GetAssociatedPhoneNumberResponse.Result.OK
                val isUnlocked =
                    phone.result == PhoneVerificationService.GetAssociatedPhoneNumberResponse.Result.UNLOCKED_TIMELOCK_ACCOUNT

                GetAssociatedPhoneNumberResponse(
                    isSuccess,
                    phone.isLinked,
                    isUnlocked,
                    phone.phoneNumber.value
                )
            }
            .flatMap { response -> Database.isInit.map { response } }
            .doOnNext { phone ->
                phoneNumber = phone.phoneNumber
                phoneLinked.value = phone.isLinked
            }
        //.onErrorResumeNext { getAssociatedPhoneNumberLocal().map { Pair(true, it) } }
    }

    fun getAssociatedPhoneNumberLocal(): Flowable<GetAssociatedPhoneNumberResponse> {
        return Flowable.zip(
            Flowable.just(phoneLinked),
            Flowable.just(phoneNumber)
        ) { v1, v2 ->
            GetAssociatedPhoneNumberResponse(true, v1.value, false, v2)
        }
    }
}

sealed interface OtpVerificationResult {
    data object Success: OtpVerificationResult
    sealed interface Error: OtpVerificationResult {
        data object InvalidPhoneNumber : Error
        data object NotInvited: Error
        data object RateLimited: Error
        data object UnsupportedPhoneType : Error
        data object UnsupportedCountry : Error
        data object UnsupportedDevice : Error
        data object Unrecognized : Error
        data object Other: Error
    }
}

sealed interface CheckVerificationResult {
    data object Success: CheckVerificationResult
    sealed interface Error: CheckVerificationResult {
        data object InvalidCode : Error
        data object NoVerification : Error
        data object Unrecognized : Error
        data object Other: Error
    }
}