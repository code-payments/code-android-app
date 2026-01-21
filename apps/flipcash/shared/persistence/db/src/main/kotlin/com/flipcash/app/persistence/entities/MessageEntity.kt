package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.getcode.solana.keys.Mint
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val idBase58: String,
    val text: String,
    val amountUsdc: Long?,
    val amountNative: Long?,
    val nativeCurrency: String?,
    val rate: Double?,
    val state: String,
    val timestamp: Long,
    val metadata: String?,
    @ColumnInfo(defaultValue = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    val mintBase58: String?,
) {
    val id: List<Byte>
        get() = Base58.decode(idBase58).toList()

    val hasAmount: Boolean
        get() = amountUsdc != null || amountNative != null

    val mint: Mint?
        get() = mintBase58?.let { Mint(it) }
}