package xyz.flipchat.services.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.getcode.model.ID
import com.getcode.model.social.user.SocialProfile
import com.getcode.model.social.user.XExtraData
import com.getcode.utils.base58
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.flipchat.services.domain.model.profile.MemberSocialProfile

@Dao
interface MemberSocialProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg profile: MemberSocialProfile)

    @Transaction
    suspend fun upsert(memberId: ID, profile: SocialProfile) {
        upsert(memberId, listOf(profile))
    }

    @Transaction
    suspend fun upsert(vararg item: Pair<ID, List<SocialProfile>>) {
        item.onEach { (id, profiles) ->
            upsert(memberId = id, profiles = profiles)
        }
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
                        verified = profile.verificationType != SocialProfile.X.VerificationType.NONE,
                        extraData = Json.encodeToString(metadata)
                    )
                }
            }
        }

        purgeSocialProfilesForMemberNotIn(models.map { it.id }, memberId.base58)
        upsert(*models.toTypedArray())
    }

    @Query("DELETE FROM social_profiles WHERE id NOT IN (:profiles) AND memberIdBase58 = :memberId")
    suspend fun purgeSocialProfilesForMemberNotIn(profiles: List<String>, memberId: String)
}