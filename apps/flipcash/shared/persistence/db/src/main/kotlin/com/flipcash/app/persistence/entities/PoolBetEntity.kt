package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.getcode.opencode.model.core.ID
import com.getcode.serialization.InstantIso8601Serializer
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "pool_bet_metadata")
data class PoolBetEntity(
    @PrimaryKey
    val idBase58: String,
    val poolIdBase58: String,
    val userIdBase58: String,
    val selectedOutcome: String,
    val payoutDestinationBase58: String,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "false")
    val hasSubmittedIntent: Boolean,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val poolId: ID = Base58.decode(poolIdBase58).toList()

    @Ignore
    val userId: ID = Base58.decode(userIdBase58).toList()

    @Ignore
    val payoutDestination: PublicKey = PublicKey.fromBase58(payoutDestinationBase58)

    @Ignore
    @Serializable(with = InstantIso8601Serializer::class)
    val placedAt: Instant = Instant.fromEpochMilliseconds(timestamp)
}
