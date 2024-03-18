package com.getcode.network.repository

import com.codeinc.gen.phone.v1.PhoneVerificationService
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
    private val networkOracle: NetworkOracle
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
    ): Flowable<PhoneVerificationService.SendVerificationCodeResponse.Result> {
        if (isMock()) return Single.just(PhoneVerificationService.SendVerificationCodeResponse.Result.OK)
            .toFlowable()

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
            }
    }

    fun checkVerificationCode(
        phoneValue: String,
        otpInput: String
    ): Flowable<PhoneVerificationService.CheckVerificationCodeResponse.Result> {
        if (isMock()) return Flowable.just(PhoneVerificationService.CheckVerificationCodeResponse.Result.OK)

        val request = PhoneVerificationService.CheckVerificationCodeRequest.newBuilder()
            .setPhoneNumber(phoneValue.toPhoneNumber())
            .setCode(PhoneVerificationService.VerificationCode.newBuilder().setValue(otpInput))
            .build()

        return phoneApi.checkVerificationCode(request)
            .map { it.result }
            .let { networkOracle.managedRequest(it) }
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