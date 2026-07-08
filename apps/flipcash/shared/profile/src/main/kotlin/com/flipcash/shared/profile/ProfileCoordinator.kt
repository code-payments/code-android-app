package com.flipcash.shared.profile

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.user.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userManager: UserManager,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("user-profile") }
    )

    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch {
            userManager.state
                .map { it.userProfile }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { profile -> persist(profile) }
        }
    }

    suspend fun restore() {
        val prefs = dataStore.data.first()
        val raw = prefs[KEY_PROFILE] ?: return
        val cached = runCatching { json.decodeFromString<CachedProfile>(raw) }.getOrNull() ?: return
        userManager.set(cached.toDomain())
    }

    suspend fun reset() {
        dataStore.edit { it.remove(KEY_PROFILE) }
    }

    private suspend fun persist(profile: UserProfile) {
        val cached = CachedProfile.fromDomain(profile)
        val raw = json.encodeToString(cached)
        dataStore.edit { it[KEY_PROFILE] = raw }
    }

    companion object {
        private val KEY_PROFILE = stringPreferencesKey("cached_user_profile")
    }
}

@Serializable
private data class CachedProfile(
    val displayName: String? = null,
    val socialAccounts: List<CachedSocialAccount> = emptyList(),
    val phoneNumber: VerifiableContactMethod? = null,
    val email: VerifiableContactMethod? = null,
) {
    fun toDomain(): UserProfile = UserProfile(
        displayName = displayName,
        socialAccounts = socialAccounts.mapNotNull { it.toDomain() },
        phoneNumber = phoneNumber,
        email = email,
    )

    companion object {
        fun fromDomain(profile: UserProfile): CachedProfile = CachedProfile(
            displayName = profile.displayName,
            socialAccounts = profile.socialAccounts.map { CachedSocialAccount.fromDomain(it) },
            phoneNumber = profile.phoneNumber,
            email = profile.email,
        )
    }
}

@Serializable
private sealed interface CachedSocialAccount {
    @Serializable
    @SerialName("twitter_x")
    data class TwitterX(
        val id: String,
        val username: String,
        val name: String,
        val description: String,
        val profilePicUrl: String,
        val verifiedType: String? = null,
        val followerCount: Int,
    ) : CachedSocialAccount

    fun toDomain(): SocialAccount? = when (this) {
        is TwitterX -> SocialAccount.TwitterX(
            id = id,
            username = username,
            name = name,
            description = description,
            profilePicUrl = profilePicUrl,
            verifiedType = verifiedType?.let {
                runCatching { SocialAccount.TwitterX.VerifiedType.valueOf(it) }.getOrNull()
            },
            followerCount = followerCount,
        )
    }

    companion object {
        fun fromDomain(account: SocialAccount): CachedSocialAccount = when (account) {
            is SocialAccount.TwitterX -> TwitterX(
                id = account.id,
                username = account.username,
                name = account.name,
                description = account.description,
                profilePicUrl = account.profilePicUrl,
                verifiedType = account.verifiedType?.name,
                followerCount = account.followerCount,
            )
        }
    }
}
