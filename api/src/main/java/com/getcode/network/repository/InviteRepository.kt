package com.getcode.network.repository

import com.codeinc.gen.invite.v2.InviteService
import com.getcode.db.InMemoryDao
import com.getcode.model.PrefsString
import com.getcode.network.api.InviteApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.asFlowable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.CoroutineContext

class InviteRepository @Inject constructor(
    private val inviteApi: InviteApi,
    private val networkOracle: NetworkOracle,
    private val identityRepository: IdentityRepository,
    private val prefRepository: PrefRepository,
    private val inMemoryDao: InMemoryDao,
    @Named("io") private val coroutineContext: CoroutineContext,
) {

    fun getInviteCount(): Flowable<Long> {
        identityRepository.getUserLocal().asFlowable()
            .flatMap { response ->
                InviteService.GetInviteCountRequest.newBuilder()
                    .setUserId(response.userId.toByteArray().toUserId())
                    .build()
                    .let { inviteApi.getInviteCount(it) }
                    .let { networkOracle.managedRequest(it) }
            }
            .subscribe(
                {
                    inMemoryDao.inviteCount = it.inviteCount
                },
                ErrorUtils::handleError
            )

        inMemoryDao.inviteCount?.let {
            return Flowable.just(it.toLong())
        }
        return Flowable.empty()
    }

    suspend fun redeem(phoneValue: String, inviteCode: String): Flowable<InviteService.InvitePhoneNumberResponse.Result> {
        return whitelist(phoneValue, inviteCode)
    }

    suspend fun whitelist(phoneValue: String): Flowable<InviteService.InvitePhoneNumberResponse.Result> {
        return whitelist(phoneValue, null)
    }

    private suspend fun whitelist(
        phoneValue: String,
        inviteCode: String? = null
    ): Flowable<InviteService.InvitePhoneNumberResponse.Result> {
        val userId = withContext(coroutineContext){
            prefRepository.getFirstOrDefault(PrefsString.KEY_USER_ID, "")
        }

        return Single.fromCallable { userId }
            .flatMapPublisher { userId ->
                val requestBuilder = InviteService.InvitePhoneNumberRequest.newBuilder().setReceiver(phoneValue.toPhoneNumber())

                val request = if (!inviteCode.isNullOrEmpty()) {
                    requestBuilder.setInviteCode(InviteService.InviteCode.newBuilder().setValue(inviteCode.trim())).build()
                } else {
                    requestBuilder.setUser(userId.decodeBase64().toUserId()).build()
                }

                inviteApi.invitePhoneNumber(request)
                    .map { it.result }
                    .toFlowable()
            }
            .flatMap { result ->
                networkOracle.managedRequest(Flowable.just(result))
            }
            .doOnComplete {
                getInviteCount().subscribe({}, {})
            }
    }


    fun getInvitationStatus(userId: ByteArray): Flowable<InvitationStatus> {
        val request =
            InviteService.GetInvitationStatusRequest.newBuilder()
                .setUserId(userId.toUserId())
                .build()

        return inviteApi.getInvitationStatus(request)
            .let { networkOracle.managedRequest(it) }
            .map {
                when (it.status) {
                    InviteService.InvitationStatus.NOT_INVITED -> InvitationStatus.NotInvited
                    InviteService.InvitationStatus.INVITED -> InvitationStatus.Invited
                    InviteService.InvitationStatus.REGISTERED -> InvitationStatus.Registered
                    InviteService.InvitationStatus.REVOKED -> InvitationStatus.Revoked
                    else -> InvitationStatus.Unrecognized
                }
            }
    }

    enum class InvitationStatus {
        NotInvited,
        Invited,
        Registered,
        Revoked,
        Unrecognized
    }
}