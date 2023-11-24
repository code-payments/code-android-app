package com.getcode.network.repository

import com.codeinc.gen.invite.v2.InviteService
import com.getcode.db.InMemoryDao
import com.getcode.model.PrefsString
import com.getcode.network.core.NetworkOracle
import com.getcode.network.api.InviteApi
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.core.Flowable
import javax.inject.Inject

class InviteRepository @Inject constructor(
    private val inviteApi: InviteApi,
    private val networkOracle: NetworkOracle,
    private val identityRepository: IdentityRepository,
    private val prefRepository: PrefRepository,
    private val inMemoryDao: InMemoryDao
) {

    fun getInviteCount(): Flowable<Long> {
        identityRepository.getUserLocal()
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

    fun redeem(phoneValue: String, inviteCode: String): Flowable<InviteService.InvitePhoneNumberResponse.Result> {
        return whitelist(phoneValue, inviteCode)
    }

    fun whitelist(phoneValue: String): Flowable<InviteService.InvitePhoneNumberResponse.Result> {
        return whitelist(phoneValue, null)
    }

    private fun whitelist(
        phoneValue: String,
        inviteCode: String? = null
    ): Flowable<InviteService.InvitePhoneNumberResponse.Result> {
        return prefRepository.getFirstOrDefault(PrefsString.KEY_USER_ID, "")
            .toFlowable()
            .flatMap { userId ->
                InviteService.InvitePhoneNumberRequest.newBuilder()
                    .setReceiver(phoneValue.toPhoneNumber())
                    .let { b ->
                        inviteCode?.trim()?.let {
                            b.setInviteCode(InviteService.InviteCode.newBuilder().setValue(it))
                        } ?: run {
                            b.setUser(userId.decodeBase64().toUserId())
                        }
                    }
                    .build()
                    .let { inviteApi.invitePhoneNumber(it) }
                    .map { it.result }
                    .let { networkOracle.managedRequest(it) }
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