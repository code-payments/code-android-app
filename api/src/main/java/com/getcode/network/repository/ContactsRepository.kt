package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.contact.v1.ContactListService
import com.getcode.ed25519.Ed25519
import com.getcode.network.api.ContactsApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.makeE164
import io.reactivex.rxjava3.core.Flowable
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ContactsRepository @Inject constructor(
    private val contactsApi: ContactsApi,
    private val networkOracle: NetworkOracle,
) {
    fun uploadContacts(
        keyPair: Ed25519.KeyPair,
        containerId: ByteArray,
        contacts: List<String>
    ): Flowable<ContactListService.AddContactsResponse> {
        if (isMock()) return Flowable.empty<ContactListService.AddContactsResponse>()
            .delay(1, TimeUnit.SECONDS)

        val request =
            ContactListService.AddContactsRequest.newBuilder()
                .setContainerId(
                    Model.DataContainerId.newBuilder().setValue(containerId.toByteString()).build()
                )
                .setOwnerAccountId(keyPair.publicKeyBytes.toSolanaAccount())
                .addAllContacts(contacts.map { contact ->
                    contact.makeE164().toPhoneNumber()
                })
                .apply { setSignature(sign(keyPair)) }
                .build()

        return contactsApi.addContacts(request)
            .let { networkOracle.managedRequest(it) }
    }

    fun getContacts(
        keyPair: Ed25519.KeyPair,
        containerId: ByteArray
    ): Flowable<List<GetContactsResponse>> {
        if (isMock()) return Flowable.just<List<GetContactsResponse>>(listOf())
            .delay(1, TimeUnit.SECONDS)

        val request =
            ContactListService.GetContactsRequest.newBuilder()
                .setContainerId(
                    Model.DataContainerId.newBuilder().setValue(containerId.toByteString()).build()
                )
                .setOwnerAccountId(keyPair.publicKeyBytes.toSolanaAccount())
                .setIncludeOnlyInAppContacts(true)
                .apply { setSignature(sign(keyPair)) }
                .build()

        return contactsApi.getContacts(request)
            .map { response ->
                response.contactsList.map { item ->
                    GetContactsResponse(
                        item.phoneNumber.value,
                        item.status.isInvited,
                        item.status.isRegistered
                    )
                }
            }
            .let { networkOracle.managedRequest(it) }
    }

    data class GetContactsResponse(
        val phoneNumber: String,
        val isInvited: Boolean,
        val isRegistered: Boolean
    )
}