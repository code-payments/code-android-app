package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.ContactListService
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.repository.ContactListRepository
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.Checksum
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.Flow

internal class InternalContactListRepository(
    private val service: ContactListService,
) : ContactListRepository {
    override suspend fun checkSync(
        owner: Ed25519.KeyPair,
        clientChecksum: Checksum,
    ): Result<Checksum> = service.checkSync(owner, clientChecksum)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun deltaUpload(
        owner: Ed25519.KeyPair,
        adds: List<ContactMethod.Phone>,
        removes: List<ContactMethod.Phone>,
        oldChecksum: Checksum,
        newChecksum: Checksum,
    ): Result<Unit> = service.deltaUpload(owner, adds, removes, oldChecksum, newChecksum)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun fullUpload(
        owner: Ed25519.KeyPair,
        phones: Flow<List<ContactMethod.Phone>>,
        expectedChecksum: Checksum,
    ): Result<Unit> = service.fullUpload(owner, phones, expectedChecksum)
        .onFailure { ErrorUtils.handleError(it) }

    override fun getFlipcashContacts(
        owner: Ed25519.KeyPair,
        checksum: Checksum,
    ): Flow<Result<List<ContactMethod.Phone>>> = service.getContacts(owner, checksum)
}
