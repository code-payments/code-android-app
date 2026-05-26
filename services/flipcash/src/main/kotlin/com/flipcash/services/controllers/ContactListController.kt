package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.repository.ContactListRepository
import com.flipcash.services.user.UserManager
import com.getcode.solana.keys.Checksum
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ContactListController @Inject constructor(
    private val repository: ContactListRepository,
    private val userManager: UserManager,
) {
    suspend fun checkSync(clientChecksum: Checksum): Result<Checksum> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.checkSync(owner, clientChecksum)
    }

    suspend fun deltaUpload(
        adds: List<ContactMethod.Phone>,
        removes: List<ContactMethod.Phone>,
        oldChecksum: Checksum,
        newChecksum: Checksum,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.deltaUpload(owner, adds, removes, oldChecksum, newChecksum)
    }

    suspend fun fullUpload(
        phones: Flow<List<ContactMethod.Phone>>,
        expectedChecksum: Checksum,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.fullUpload(owner, phones, expectedChecksum)
    }

    fun getFlipcashContacts(checksum: Checksum): Flow<Result<List<ContactMethod.Phone>>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: throw IllegalStateException("No account cluster in UserManager")
        return repository.getFlipcashContacts(owner, checksum)
    }
}
