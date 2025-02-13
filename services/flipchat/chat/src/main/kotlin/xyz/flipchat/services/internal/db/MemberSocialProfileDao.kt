package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.getcode.model.ID
import com.getcode.model.social.user.SocialProfile
import com.getcode.utils.base58
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.flipchat.services.domain.model.profile.MemberSocialProfile
import xyz.flipchat.services.domain.model.profile.XExtraData

@Dao
interface MemberSocialProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg profile: MemberSocialProfile)

    @Transaction
    suspend fun upsert(memberId: ID, profile: SocialProfile) {
        upsert(memberId, listOf(profile))
    }

    @Transaction
    suspend fun upsert(memberId: ID, profiles: List<SocialProfile>) {
        val models = profiles.mapNotNull { profile ->
            when (profile) {
                is SocialProfile.Unknown -> null
                is SocialProfile.X -> {
                    val metadata = XExtraData(
                        friendlyName = profile.friendlyName,
                        description = profile.description,
                        verificationType = profile.verificationType,
                        followerCount = profile.followerCount
                    )

                    MemberSocialProfile(
                        id = profile.id,
                        memberIdBase58 = memberId.base58,
                        username = profile.username,
                        profileImageUrl = profile.profilePicUrl,
                        platformType = "x",
                        extraData = Json.encodeToString(metadata)
                    )
                }
            }
        }

        upsert(*models.toTypedArray())
    }

    @Query("DELETE FROM social_profiles WHERE memberIdBase58 = :memberId")
    suspend fun deleteForMember(memberId: String)
    suspend fun deleteForMember(memberId: ID) {
        deleteForMember(memberId.base58)
    }

    @Query("DELETE FROM social_profiles WHERE memberIdBase58 = :memberId AND id = :socialProfileId")
    suspend fun unlinkSocialProfile(memberId: String, socialProfileId: String)
    suspend fun unlinkSocialProfile(memberId: ID, socialProfileId: String) {
        unlinkSocialProfile(memberId.base58, socialProfileId)
    }
}