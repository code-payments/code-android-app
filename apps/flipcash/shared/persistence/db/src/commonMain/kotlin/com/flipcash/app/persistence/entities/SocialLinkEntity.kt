package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.opencode.model.financial.SocialLink

@Entity(
    tableName = "token_social_links",
    foreignKeys = [
        ForeignKey(
            entity = TokenEntity::class,
            parentColumns = ["address"],
            childColumns = ["token_address"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("token_address")]
)
data class SocialLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "token_address")
    val tokenAddress: String,

    @ColumnInfo(name = "type")
    val type: SocialLinkType,

    @ColumnInfo(name = "value")
    val value: String,
) {
    @get:Ignore
    val socialLink: SocialLink
        get() = when (type) {
            SocialLinkType.WEBSITE -> SocialLink.Website(value)
            SocialLinkType.X -> SocialLink.X(value)
        }
}

enum class SocialLinkType {
    WEBSITE, X
}

data class TokenWithValuation(
    @Embedded val token: TokenEntity,
    @Relation(
        parentColumn = "address",
        entityColumn = "token_address"
    )
    val valuation: TokenValuationEntity
)

data class TokenWithSocialLinks(
    @Embedded val token: TokenEntity,
    @Relation(
        parentColumn = "address",
        entityColumn = "token_address"
    )
    val socialLinks: List<SocialLinkEntity>,
)

data class TokenWithSocialLinksAndValuation(
    @Embedded val token: TokenEntity,
    @Relation(
        parentColumn = "address",
        entityColumn = "token_address"
    )
    val socialLinks: List<SocialLinkEntity>,
    @Relation(
        parentColumn = "address",
        entityColumn = "token_address"
    )
    val valuation: TokenValuationEntity
)