package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.contact.v1.ContactListGrpcKt
import com.codeinc.flipcash.gen.phone.v1.Model
import com.codeinc.flipcash.gen.contact.v1.validate
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asHash
import com.flipcash.services.internal.network.extensions.authenticate
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.solana.keys.Checksum
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.codeinc.flipcash.gen.contact.v1.ContactListService as RpcContactListService

@Singleton
internal class ContactListApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ContactListGrpcKt.ContactListCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun checkSync(
        owner: KeyPair,
        clientChecksum: Checksum,
    ): RpcContactListService.CheckSyncResponse {
        val request = RpcContactListService.CheckSyncRequest.newBuilder()
            .setClientChecksum(clientChecksum.asHash())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.checkSync(request)
        }
    }

    suspend fun deltaUpload(
        owner: KeyPair,
        adds: List<ContactMethod.Phone>,
        removes: List<ContactMethod.Phone>,
        oldChecksum: Checksum,
        newChecksum: Checksum,
    ): RpcContactListService.DeltaUploadResponse {
        val request = RpcContactListService.DeltaUploadRequest.newBuilder()
            .addAllAdds(adds.map { Model.PhoneNumber.newBuilder().setValue(it.phoneNumber).build() })
            .addAllRemoves(removes.map { Model.PhoneNumber.newBuilder().setValue(it.phoneNumber).build() })
            .setOldChecksum(oldChecksum.asHash())
            .setNewChecksum(newChecksum.asHash())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.deltaUpload(request)
        }
    }

    suspend fun fullUpload(
        owner: KeyPair,
        phones: Flow<List<ContactMethod.Phone>>,
        expectedChecksum: Checksum,
    ): RpcContactListService.FullUploadResponse {
        val requestFlow = kotlinx.coroutines.flow.flow {
            phones.collect { batch ->
                val request = RpcContactListService.FullUploadRequest.newBuilder()
                    .addAllPhones(batch.map { Model.PhoneNumber.newBuilder().setValue(it.phoneNumber).build() })
                    .setExpectedChecksum(expectedChecksum.asHash())
                    .apply { setAuth(authenticate(owner)) }
                    .build()

                request.validate().orThrow()
                emit(request)
            }
        }

        return withContext(Dispatchers.IO) {
            api.fullUpload(requestFlow)
        }
    }

    fun getFlipcashContacts(
        owner: KeyPair,
        checksum: Checksum,
    ): Flow<RpcContactListService.GetFlipcashContactsResponse> {
        val request = RpcContactListService.GetFlipcashContactsRequest.newBuilder()
            .setChecksum(checksum.asHash())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api.getFlipcashContacts(request)
    }
}
