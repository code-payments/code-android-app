package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.flipcash.app.persistence.embedded.LaunchpadMetadataEmbedded
import com.flipcash.app.persistence.embedded.VmMetadataEmbedded
import com.getcode.solana.keys.Mint

@Entity(tableName = "tokens")
data class TokenEntity(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "decimals")
    val decimals: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "symbol")
    val symbol: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long?,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String,

    @Embedded(prefix = "vm_")
    val vmMetadata: VmMetadataEmbedded,

    @Embedded(prefix = "lp_")
    val launchpadMetadata: LaunchpadMetadataEmbedded?,

    // Serialized via TypeConverter (polymorphic sealed types)
    @ColumnInfo(name = "social_links")
    val socialLinks: String?, // JSON

    // Serialized via TypeConverter (polymorphic sealed types)
    @ColumnInfo(name = "bill_customizations")
    val billCustomizationsJson: String?, // JSON
) {
    @get:Ignore
    val mint: Mint
        get() = Mint(address)
}

/**
 * Room relation joining [TokenEntity] with its optional [TokenValuationEntity].
 * Used for single-query hydration of the full token + valuation state.
 */
data class TokenWithBalanceRelation(
    @Embedded val token: TokenEntity,
    @Relation(
        parentColumn = "address",
        entityColumn = "token_address"
    )
    val valuation: TokenValuationEntity?,
)