package com.flipcash.services.internal.network.services

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.FlipcashContactEntry
import com.flipcash.services.internal.network.api.ContactListApi
import com.flipcash.services.internal.network.extensions.toChecksum
import com.flipcash.services.internal.network.extensions.toChatId
import com.flipcash.services.models.CheckSyncError
import com.flipcash.services.models.DeltaUploadError
import com.flipcash.services.models.FullUploadError
import com.flipcash.services.models.GetContactsError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.Checksum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.codeinc.flipcash.gen.contact.v1.ContactListService as RpcContactListService

internal class ContactListService @Inject constructor(
    private val api: ContactListApi,
) {
    suspend fun checkSync(
        owner: KeyPair,
        clientChecksum: Checksum,
    ): Result<Checksum> {
        return runCatching {
            api.checkSync(owner, clientChecksum)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcContactListService.CheckSyncResponse.Result.OK ->
                        Result.success(response.serverChecksum.toChecksum())
                    RpcContactListService.CheckSyncResponse.Result.DENIED ->
                        Result.failure(CheckSyncError.Denied())
                    RpcContactListService.CheckSyncResponse.Result.OUT_OF_SYNC ->
                        Result.failure(CheckSyncError.OutOfSync(response.serverChecksum.toChecksum()))
                    RpcContactListService.CheckSyncResponse.Result.UNRECOGNIZED ->
                        Result.failure(CheckSyncError.Unrecognized())
                    else -> Result.failure(CheckSyncError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { CheckSyncError.Other(cause = it) })
            }
        )
    }

    suspend fun deltaUpload(
        owner: KeyPair,
        adds: List<ContactMethod.Phone>,
        removes: List<ContactMethod.Phone>,
        oldChecksum: Checksum,
        newChecksum: Checksum,
    ): Result<Unit> {
        return runCatching {
            api.deltaUpload(owner, adds, removes, oldChecksum, newChecksum)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcContactListService.DeltaUploadResponse.Result.OK ->
                        Result.success(Unit)
                    RpcContactListService.DeltaUploadResponse.Result.DENIED ->
                        Result.failure(DeltaUploadError.Denied())
                    RpcContactListService.DeltaUploadResponse.Result.CHECKSUM_MISMATCH ->
                        Result.failure(DeltaUploadError.ChecksumMismatch())
                    RpcContactListService.DeltaUploadResponse.Result.CHECKSUM_DRIFT ->
                        Result.failure(DeltaUploadError.ChecksumDrift())
                    RpcContactListService.DeltaUploadResponse.Result.TOO_MANY_CONTACTS ->
                        Result.failure(DeltaUploadError.TooManyContacts())
                    RpcContactListService.DeltaUploadResponse.Result.UNRECOGNIZED ->
                        Result.failure(DeltaUploadError.Unrecognized())
                    else -> Result.failure(DeltaUploadError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { DeltaUploadError.Other(cause = it) })
            }
        )
    }

    suspend fun fullUpload(
        owner: KeyPair,
        phones: Flow<List<ContactMethod.Phone>>,
        expectedChecksum: Checksum,
    ): Result<Unit> {
        return runCatching {
            api.fullUpload(owner, phones, expectedChecksum)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcContactListService.FullUploadResponse.Result.OK ->
                        Result.success(Unit)
                    RpcContactListService.FullUploadResponse.Result.DENIED ->
                        Result.failure(FullUploadError.Denied())
                    RpcContactListService.FullUploadResponse.Result.CHECKSUM_MISMATCH ->
                        Result.failure(FullUploadError.ChecksumMismatch())
                    RpcContactListService.FullUploadResponse.Result.TOO_MANY_CONTACTS ->
                        Result.failure(FullUploadError.TooManyContacts())
                    RpcContactListService.FullUploadResponse.Result.UNRECOGNIZED ->
                        Result.failure(FullUploadError.Unrecognized())
                    else -> Result.failure(FullUploadError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { FullUploadError.Other(cause = it) })
            }
        )
    }

    fun getContacts(
        owner: KeyPair,
        checksum: Checksum,
    ): Flow<Result<List<FlipcashContactEntry>>> {
        return api.getFlipcashContacts(owner, checksum).map { response ->
            when (response.result) {
                RpcContactListService.GetFlipcashContactsResponse.Result.OK ->
                    Result.success(response.contactsList.map { proto ->
                        FlipcashContactEntry(
                            phoneNumber = proto.phone.value,
                            dmChatId = proto.dmChatId
                                .takeIf { !it.value.isEmpty }
                                ?.toChatId(),
                        )
                    })
                RpcContactListService.GetFlipcashContactsResponse.Result.DENIED ->
                    Result.failure(GetContactsError.Denied())
                RpcContactListService.GetFlipcashContactsResponse.Result.NOT_FOUND ->
                    Result.failure(GetContactsError.NotFound())
                RpcContactListService.GetFlipcashContactsResponse.Result.CHECKSUM_DRIFT ->
                    Result.failure(GetContactsError.ChecksumDrift())
                RpcContactListService.GetFlipcashContactsResponse.Result.UNRECOGNIZED ->
                    Result.failure(GetContactsError.Unrecognized())
                else -> Result.failure(GetContactsError.Other())
            }
        }
    }
}
