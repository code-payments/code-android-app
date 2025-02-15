package xyz.flipchat.services.domain.model.people

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import xyz.flipchat.services.domain.model.profile.MemberSocialProfile
import xyz.flipchat.services.internal.data.mapper.nullIfEmpty

@Serializable
@Entity(tableName = "users",)
data class FlipchatUser(
    @PrimaryKey
    val userIdBase58: String,
    val memberName: String?,
    val imageUri: String?,
    val isBlocked: Boolean? = null
) {
    @Ignore
    val id: ID = Base58.decode(userIdBase58).toList()
}

data class FlipchatUserWithSocialProfiles(
    @Embedded val user: FlipchatUser,
    @Relation(
        parentColumn = "userIdBase58",
        entityColumn = "memberIdBase58",
        entity = MemberSocialProfile::class,
    )
    val profiles: List<MemberSocialProfile>
) {
    val imageData: Any
        get() {
            return profiles.firstOrNull()?.profileImageUrl.nullIfEmpty()
                ?: user.imageUri.nullIfEmpty()
                ?: user.id
        }
}

data class MemberPersonalInfo(
    val memberName: String?,
    val imageUri: String?,
    val isBlocked: Boolean
)
