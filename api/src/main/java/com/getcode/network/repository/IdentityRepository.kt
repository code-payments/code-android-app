package com.getcode.network.repository

import com.codeinc.gen.phone.v1.PhoneVerificationService
import com.codeinc.gen.user.v1.IdentityService
import com.getcode.db.Database
import com.getcode.ed25519.Ed25519
import com.getcode.model.AirdropType
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.network.core.NetworkOracle
import com.getcode.network.api.IdentityApi
import com.google.common.collect.Sets
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class IdentityRepository @Inject constructor(
    private val identityApi: IdentityApi,
    private val networkOracle: NetworkOracle,
    private val prefRepository: PrefRepository,
    private val phoneRepository: PhoneRepository
) {
    data class GetUserResponse(
        val userId: List<Byte>,
        val dataContainerId: List<Byte>,
        val enableDebugOptions: Boolean,
        val eligibleAirdrops : Set<AirdropType>,
        val isPhoneNumberLinked: Boolean
    )

    fun getUser(
        keyPair: Ed25519.KeyPair,
        phoneValue: String
    ): Flowable<GetUserResponse> {
        if (isMock()) return Flowable.just(GetUserResponse(listOf(), listOf(), false, setOf(), true))

        val request =
            IdentityService.GetUserRequest.newBuilder()
                .setPhoneNumber(phoneValue.toPhoneNumber())
                .setOwnerAccountId(keyPair.publicKeyBytes.toSolanaAccount())
                .let {
                    val bos = ByteArrayOutputStream()
                    it.buildPartial().writeTo(bos)
                    it.setSignature(Ed25519.sign(bos.toByteArray(), keyPair).toSignature())
                }
                .build()

        return identityApi.getUser(request)
            .map {
                GetUserResponse(
                    userId = it.user?.id?.value?.toList() ?: throw Exception("Error: Null data"),
                    dataContainerId = it.dataContainerId?.value?.toList() ?: throw Exception("Error: Null data"),
                    enableDebugOptions = it.enableInternalFlags,
                    eligibleAirdrops = it.eligibleAirdropsList?.mapNotNull { value -> AirdropType.getInstance(value) }?.toSet() ?: throw Exception("Error: Null data"),
                    isPhoneNumberLinked = it.phone?.isLinked ?: throw Exception("Error: Null data")
                )
            }
            .let { networkOracle.managedRequest(it) }
            .doOnNext { user ->
                // TODO: There's some duplicated DB saving code in AuthManager. It's possible for
                //       view inconsistencies with expected state, since I suspect Database.isOpen()
                //       is false by the time we execute this piece of code in some flows (eg. in
                //       particular I've noticed logging in through seed phrase input).
                if (Database.isOpen()) {
                    prefRepository.set(
                        PrefsString.KEY_USER_ID,
                        user.userId.toByteArray().encodeBase64()
                    )
                    prefRepository.set(
                        PrefsString.KEY_DATA_CONTAINER_ID,
                        user.dataContainerId.toByteArray().encodeBase64()
                    )
                    phoneRepository.phoneLinked.value = true
                    prefRepository.set(
                        PrefsBool.IS_DEBUG_ALLOWED,
                        user.enableDebugOptions
                    )
                    prefRepository.set(
                        PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP,
                        user.eligibleAirdrops.contains(AirdropType.GetFirstKin)
                    )
                    prefRepository.set(
                        PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP,
                        user.eligibleAirdrops.contains(AirdropType.GiveFirstKin)
                    )
                }
            }
    }

    fun getUserLocal(): Flowable<GetUserResponse> {
        return Flowable.zip(
            prefRepository.getFlowable(PrefsString.KEY_USER_ID),
            prefRepository.getFlowable(PrefsString.KEY_DATA_CONTAINER_ID),
            prefRepository.getFlowable(PrefsBool.IS_DEBUG_ALLOWED),
            prefRepository.getFlowable(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP),
            prefRepository.getFlowable(PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP),
            Flowable.just(phoneRepository.phoneLinked)
        ) { userId, dataContainerId, isDebugAllowed, isEligibleGetFirstKinAirdrop, isEligibleGiveFirstKinAirdrop, isPhoneNumberLinked ->
            var eligibleAirdrops = Sets.newHashSet<AirdropType>()
            if (isEligibleGetFirstKinAirdrop) {
                eligibleAirdrops.add(AirdropType.GetFirstKin)
            }
            if (isEligibleGiveFirstKinAirdrop) {
                eligibleAirdrops.add(AirdropType.GiveFirstKin)
            }
            GetUserResponse(
                userId = userId.decodeBase64().toList(),
                dataContainerId = dataContainerId.decodeBase64().toList(),
                enableDebugOptions = isDebugAllowed,
                eligibleAirdrops = eligibleAirdrops,
                isPhoneNumberLinked = isPhoneNumberLinked.value
            )
        }
            .filter { it.userId.isNotEmpty() && it.dataContainerId.isNotEmpty() }
            .distinctUntilChanged()
    }


    fun linkAccount(
        keyPair: Ed25519.KeyPair,
        phoneValue: String,
        code: String
    ): Single<IdentityService.LinkAccountResponse.Result> {
        if (isMock()) return Single.just(IdentityService.LinkAccountResponse.Result.OK)
            .delay(1, TimeUnit.SECONDS)

        val request =
            IdentityService.LinkAccountRequest.newBuilder()
                .setPhone(
                    PhoneVerificationService.PhoneLinkingToken.newBuilder()
                        .setPhoneNumber(phoneValue.toPhoneNumber())
                        .setCode(
                            PhoneVerificationService.VerificationCode.newBuilder()
                                .setValue(code)
                        )
                )
                .setOwnerAccountId(keyPair.publicKeyBytes.toSolanaAccount())

                .let {
                    val bos = ByteArrayOutputStream()
                    it.buildPartial().writeTo(bos)
                    it.setSignature(Ed25519.sign(bos.toByteArray(), keyPair).toSignature())
                }
                .build()

        return identityApi.linkAccount(request)
            .map { it.result }
            .let { networkOracle.managedRequest(it) }
            .firstOrError()
    }

    fun unlinkAccount(
        keyPair: Ed25519.KeyPair,
        phoneValue: String
    ): Single<IdentityService.UnlinkAccountResponse.Result> {
        if (isMock()) return Single.just(IdentityService.UnlinkAccountResponse.Result.OK)
            .delay(1, TimeUnit.SECONDS)

        val request =
            IdentityService.UnlinkAccountRequest.newBuilder()
                .setPhoneNumber(phoneValue.toPhoneNumber())
                .setOwnerAccountId(keyPair.publicKeyBytes.toSolanaAccount())

                .let {
                    val bos = ByteArrayOutputStream()
                    it.buildPartial().writeTo(bos)
                    it.setSignature(Ed25519.sign(bos.toByteArray(), keyPair).toSignature())
                }
                .build()

        return identityApi.unlinkAccount(request)
            .map { it.result }
            .let { networkOracle.managedRequest(it) }
            .doOnComplete {
                phoneRepository.phoneNumber = ""
                phoneRepository.phoneLinked.value = false
            }
            .firstOrError()
    }
}