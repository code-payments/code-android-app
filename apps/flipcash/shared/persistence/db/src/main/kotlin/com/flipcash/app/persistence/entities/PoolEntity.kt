package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.io.Serial

@Serializable
@Entity(tableName = "pool_metadata")
data class PoolEntity(
    @PrimaryKey
    val idBase58: String,
    val creatorBase58: String,
    val pagingTokenBase58: String?,
    val name: String,
    val buyInAmount: Long,
    val buyInCurrency: String,
    val fundingDestinationBase58: String,
    val isOpen: Boolean,
    val didWin: Boolean,
    val resolution: String?,
    val timestamp: Long,
    val closedTimestamp: Long?,
    @ColumnInfo(defaultValue = "-1")
    val derivationIndex: Long,
    val rendezvousSignature: String?,
    val betSummary: String?,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val creator: ID = Base58.decode(creatorBase58).toList()

    @Ignore
    val pagingToken: ID? = pagingTokenBase58?.let { Base58.decode(it).toList() }

    @Ignore
    val fundingDestination: PublicKey = PublicKey.fromBase58(fundingDestinationBase58)

    @Ignore
    val createdAt: Instant = Instant.fromEpochMilliseconds(timestamp)

    @Ignore
    val closedAt: Instant? = closedTimestamp?.let { Instant.fromEpochMilliseconds(it) }
}

data class PoolWithBetsEntity(
    @Embedded val pool: PoolEntity,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "poolIdBase58"
    )
    val bets: List<PoolBetEntity>
)