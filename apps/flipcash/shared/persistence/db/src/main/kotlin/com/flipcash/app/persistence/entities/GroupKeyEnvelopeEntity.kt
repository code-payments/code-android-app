package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A private group's chat key envelope as the server returned it for the viewer, one row per
 * (chat, own public key). The envelope is still wrapped: the chat key itself is never written
 * here, so the file holds nothing that opens a chat without the viewer's private key.
 *
 * The own public key is part of the key so an envelope wrapped for one account is never opened
 * as another's.
 */
@Entity(
    tableName = "group_key_envelopes",
    primaryKeys = ["chat_id_hex", "own_pk_hex"],
)
data class GroupKeyEnvelopeEntity(
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    @ColumnInfo(name = "own_pk_hex")
    val ownPkHex: String,
    // The raw `KeyEnvelope.Scheme` number, so an unknown scheme round-trips and is refused on read.
    @ColumnInfo(name = "scheme")
    val scheme: Int,
    @ColumnInfo(name = "nonce", typeAffinity = ColumnInfo.BLOB)
    val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext", typeAffinity = ColumnInfo.BLOB)
    val ciphertext: ByteArray,
    // The user whose key wrapped it; null when the server did not say (read as the viewer's own).
    @ColumnInfo(name = "wrapped_by", typeAffinity = ColumnInfo.BLOB)
    val wrappedBy: ByteArray?,
    // The key the server reported for the wrapper, a hint only.
    @ColumnInfo(name = "wrapped_by_public_key", typeAffinity = ColumnInfo.BLOB)
    val wrappedByPublicKey: ByteArray?,
) {
    // Arrays are compared by content, unlike a data class's default.
    override fun equals(other: Any?): Boolean =
        this === other || (other is GroupKeyEnvelopeEntity &&
            chatIdHex == other.chatIdHex && ownPkHex == other.ownPkHex && scheme == other.scheme &&
            nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext) &&
            wrappedBy.contentEquals(other.wrappedBy) &&
            wrappedByPublicKey.contentEquals(other.wrappedByPublicKey))

    override fun hashCode(): Int = 31 * chatIdHex.hashCode() + ownPkHex.hashCode()
}
