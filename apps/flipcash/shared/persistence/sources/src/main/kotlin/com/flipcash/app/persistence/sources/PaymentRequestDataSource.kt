package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.PaymentRequestEntity
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.decodePaymentRequest
import com.flipcash.services.models.paymentrequest.encodePaymentRequest
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `payment_requests` table: each request as the serialized `PaymentRequestMetadata` it was
 * received as, with the columns the merge and the per-chat query read.
 *
 * Text is stored in its encrypted form, so what [inChat] and [find] return still needs opening.
 * No database means nothing stored rather than an error, like the other data sources here.
 */
@Singleton
class PaymentRequestDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    /**
     * Writes each request unless the stored copy is newer (the rule is in
     * `PaymentRequestDao.mergeIfNewer`, enforced by the database).
     */
    suspend fun merge(requests: List<PaymentRequest>) {
        val dao = db?.paymentRequestDao() ?: return
        requests.forEach { dao.mergeIfNewer(it.toEntity()) }
    }

    suspend fun inChat(chatId: ChatId): List<PaymentRequest> =
        db?.paymentRequestDao()?.inChat(chatId.bytes.toList().hexEncodedString())
            ?.mapNotNull { it.toRequest() }
            .orEmpty()

    suspend fun find(id: ID): PaymentRequest? =
        db?.paymentRequestDao()?.find(id.hexEncodedString())?.toRequest()

    suspend fun clear() {
        db?.paymentRequestDao()?.deleteAll()
    }

    // A row whose bytes no longer parse reads as absent; the next fetch replaces it.
    private fun PaymentRequestEntity.toRequest(): PaymentRequest? = decodePaymentRequest(request)

    private fun PaymentRequest.toEntity() = PaymentRequestEntity(
        idHex = id.hexEncodedString(),
        chatIdHex = chatId?.bytes?.toList()?.hexEncodedString().orEmpty(),
        version = version,
        status = status.toColumn(),
        endsAtEpochMs = split?.endsAt?.toEpochMilliseconds(),
        createdAtEpochMs = createdAt.toEpochMilliseconds(),
        request = encodePaymentRequest(),
    )

    private fun PaymentRequestStatus.toColumn(): String = when (this) {
        PaymentRequestStatus.Open -> PaymentRequestEntity.STATUS_OPEN
        PaymentRequestStatus.Completed -> PaymentRequestEntity.STATUS_COMPLETED
        PaymentRequestStatus.Closed -> PaymentRequestEntity.STATUS_CLOSED
        PaymentRequestStatus.Expired -> PaymentRequestEntity.STATUS_EXPIRED
        PaymentRequestStatus.Unknown -> PaymentRequestEntity.STATUS_UNKNOWN
    }
}
