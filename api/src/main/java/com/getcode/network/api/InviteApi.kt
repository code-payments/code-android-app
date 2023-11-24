package com.getcode.network.api

import com.codeinc.gen.invite.v2.InviteGrpc
import com.codeinc.gen.invite.v2.InviteService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class InviteApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = InviteGrpc.newStub(managedChannel)

    fun invitePhoneNumber(request: InviteService.InvitePhoneNumberRequest): Single<InviteService.InvitePhoneNumberResponse> {
        return api::invitePhoneNumber
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getInviteCount(request: InviteService.GetInviteCountRequest): Single<InviteService.GetInviteCountResponse> {
        return api::getInviteCount
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getInvitationStatus(request: InviteService.GetInvitationStatusRequest): Single<InviteService.GetInvitationStatusResponse> {
        return api::getInvitationStatus
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
}
