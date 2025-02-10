package xyz.flipchat.services.domain.model.people

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

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

data class MemberPersonalInfo(
    val memberName: String?,
    val imageUri: String?,
    val isBlocked: Boolean
)
