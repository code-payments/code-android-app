package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.phone.v1.PhoneVerificationService
import com.codeinc.gen.user.v1.IdentityService
import com.codeinc.gen.user.v1.IdentityService.LoginToThirdPartyAppRequest
import com.codeinc.gen.user.v1.IdentityService.LoginToThirdPartyAppResponse
import com.codeinc.gen.user.v1.IdentityService.UpdatePreferencesRequest
import com.codeinc.gen.user.v1.IdentityService.User
import com.getcode.db.Database
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.AirdropType
import com.getcode.model.PrefsBool
import com.getcode.model.PrefsString
import com.getcode.network.core.NetworkOracle
import com.getcode.network.api.IdentityApi
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import com.google.common.collect.Sets
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.Locale
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
        val eligibleAirdrops: Set<AirdropType>,
        val isPhoneNumberLinked: Boolean
    )

    fun getUser(
        keyPair: Ed25519.KeyPair,
        phoneValue: String
    ): Flowable<GetUserResponse> {
        if (isMock()) return Flowable.just(
            GetUserResponse(
                listOf(),
                listOf(),
                false,
                setOf(),
                true
            )
        )

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
                    dataContainerId = it.dataContainerId?.value?.toList()
                        ?: throw Exception("Error: Null data"),
                    enableDebugOptions = it.enableInternalFlags,
                    eligibleAirdrops = it.eligibleAirdropsList?.mapNotNull { value ->
                        AirdropType.getInstance(
                            value
                        )
                    }?.toSet() ?: throw Exception("Error: Null data"),
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

    suspend fun getUserContainerId() = prefRepository.get(
        PrefsString.KEY_DATA_CONTAINER_ID,
        ""
    ).decodeBase64().toList()

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

    suspend fun updatePreferences(
        locale: Locale,
        owner: KeyPair,
    ): Result<Unit> {
        val localeTag = locale.toLanguageTag()
        Timber.i("Attempting to update locale to $localeTag")
        val containerId = getUserContainerId()
        val request = UpdatePreferencesRequest.newBuilder()
            .setContainerId(
                Model.DataContainerId.newBuilder().setValue(containerId.toByteString()).build()
            ).setOwnerAccountId(owner.publicKeyBytes.toSolanaAccount())
            .setLocale(
                Model.Locale.newBuilder().setValue(locale.toLanguageTag()).build()
            ).apply {
                setSignature(sign(owner))
            }.build()


        return try {
            networkOracle.managedRequest(identityApi.updatePreferences(request))
                .map { response ->
                    when (val result = response.result) {
                        IdentityService.UpdatePreferencesResponse.Result.OK -> {
                            Timber.d("updatePreferences success = locale set: ${localeTag}")
                            Result.success(Unit)
                        }

                        IdentityService.UpdatePreferencesResponse.Result.INVALID_LOCALE -> {
                            val error = Throwable("Error: (${localeTag}) ${result.name}")
                            ErrorUtils.handleError(error)
                            Result.failure(error)
                        }

                        IdentityService.UpdatePreferencesResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: updatePreferences Unrecognized request.")
                            ErrorUtils.handleError(error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: updatePreferences Unknown Error")
                            ErrorUtils.handleError(error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val msg = Throwable("Locale:: $localeTag " + e.localizedMessage)
            ErrorUtils.handleError(msg)
            Result.failure(msg)
        }
    }

    suspend fun loginToThirdParty(
        rendezvous: PublicKey,
        relationship: KeyPair
    ): Result<Unit> {
        val request = LoginToThirdPartyAppRequest.newBuilder()
            .setIntentId(rendezvous.toIntentId())
            .setUserId(relationship.publicKeyBytes.toSolanaAccount())
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), relationship).toSignature())
            }.build()

        return try {
            networkOracle.managedRequest(identityApi.loginToThirdParty(request))
                .map { response ->
                    when (val result = response.result) {
                        LoginToThirdPartyAppResponse.Result.OK -> Result.success(Unit)
                        LoginToThirdPartyAppResponse.Result.REQUEST_NOT_FOUND,
                        LoginToThirdPartyAppResponse.Result.PAYMENT_REQUIRED,
                        LoginToThirdPartyAppResponse.Result.LOGIN_NOT_SUPPORTED,
                        LoginToThirdPartyAppResponse.Result.DIFFERENT_LOGIN_EXISTS,
                        LoginToThirdPartyAppResponse.Result.INVALID_ACCOUNT -> {
                            val error = Throwable("Error: ${result.name}")
                            ErrorUtils.handleError(error)
                            Result.failure(error)
                        }

                        LoginToThirdPartyAppResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: loginToThirdParty Unrecognized request.")
                            ErrorUtils.handleError(error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = Throwable("Error: loginToThirdParty Unknown")
                            ErrorUtils.handleError(error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }
}