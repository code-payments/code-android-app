package com.flipcash.services.repository

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.FlipcashContactEntry
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.Checksum
import kotlinx.coroutines.flow.Flow

interface ContactListRepository {
    suspend fun checkSync(
        owner: KeyPair,
        clientChecksum: Checksum,
    ): Result<Checksum>

    suspend fun deltaUpload(
        owner: KeyPair,
        adds: List<ContactMethod.Phone>,
        removes: List<ContactMethod.Phone>,
        oldChecksum: Checksum,
        newChecksum: Checksum,
    ): Result<Unit>

    suspend fun fullUpload(
        owner: KeyPair,
        phones: Flow<List<ContactMethod.Phone>>,
        expectedChecksum: Checksum,
    ): Result<Unit>

    fun getFlipcashContacts(
        owner: KeyPair,
        checksum: Checksum,
    ): Flow<Result<List<FlipcashContactEntry>>>
}
