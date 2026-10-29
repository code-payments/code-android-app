package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import com.flipcash.app.persistence.entities.PaymentRequestEntity

@Dao
interface PaymentRequestDao {

    /**
     * Stores a request unless the stored copy is newer, by the `paymentrequest.v1` merge rule:
     * written when there is no row, when [version] is greater than the stored one, or at an equal
     * version when the incoming copy is EXPIRED and the stored one is OPEN (expiry moves no
     * version). A lower version, or an equal one that is anything else, leaves the row alone. The
     * whole row is replaced, as the contract has clients replace the message whole.
     *
     * One statement, so the compare and the write are atomic and a stale write never overwrites a
     * newer row however writers interleave.
     */
    @Query(
        "INSERT INTO payment_requests " +
            "(id_hex, chat_id_hex, version, status, ends_at, created_at, request) " +
            "VALUES (:idHex, :chatIdHex, :version, :status, :endsAtEpochMs, :createdAtEpochMs, :request) " +
            "ON CONFLICT(id_hex) DO UPDATE SET " +
            "chat_id_hex = excluded.chat_id_hex, version = excluded.version, status = excluded.status, " +
            "ends_at = excluded.ends_at, created_at = excluded.created_at, request = excluded.request " +
            "WHERE excluded.version > payment_requests.version " +
            "OR (excluded.version = payment_requests.version " +
            "AND excluded.status = 'EXPIRED' AND payment_requests.status = 'OPEN')"
    )
    suspend fun mergeIfNewer(
        idHex: String,
        chatIdHex: String,
        version: Long,
        status: String,
        endsAtEpochMs: Long?,
        createdAtEpochMs: Long,
        request: ByteArray,
    )

    suspend fun mergeIfNewer(entity: PaymentRequestEntity) = mergeIfNewer(
        idHex = entity.idHex,
        chatIdHex = entity.chatIdHex,
        version = entity.version,
        status = entity.status,
        endsAtEpochMs = entity.endsAtEpochMs,
        createdAtEpochMs = entity.createdAtEpochMs,
        request = entity.request,
    )

    @Query("SELECT * FROM payment_requests WHERE id_hex = :idHex")
    suspend fun find(idHex: String): PaymentRequestEntity?

    @Query("SELECT * FROM payment_requests WHERE chat_id_hex = :chatIdHex")
    suspend fun inChat(chatIdHex: String): List<PaymentRequestEntity>

    @Query("DELETE FROM payment_requests")
    suspend fun deleteAll()
}
