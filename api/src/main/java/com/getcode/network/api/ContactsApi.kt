package com.getcode.network.api

import com.codeinc.gen.contact.v1.ContactListGrpc
import com.codeinc.gen.contact.v1.ContactListService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject


class ContactsApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = ContactListGrpc.newStub(managedChannel)

    fun getContacts(request: ContactListService.GetContactsRequest): Single<ContactListService.GetContactsResponse> {
        return api::getContacts
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
    fun addContacts(request: ContactListService.AddContactsRequest): Single<ContactListService.AddContactsResponse> {
        return api::addContacts
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
    fun removeContacts(request: ContactListService.RemoveContactsRequest): Single<ContactListService.RemoveContactsResponse> {
        return api::removeContacts
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
}