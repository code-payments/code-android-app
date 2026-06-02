package com.flipcash.app.auth.internal.credentials

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.base58
import com.getcode.utils.encodeBase64
import com.getcode.vendor.Base58
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassphraseCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountController: AccountController,
    private val userManager: UserManager,
    private val mnemonicManager: MnemonicManager,
    private val featureFlags: FeatureFlagController,
) {
    companion object {
        private val temporaryEntropyKey = stringPreferencesKey("temporaryEntropy")
        private val temporaryUserIdKey = stringPreferencesKey("temporaryUserId")
        private fun seenAccessKeyKey(accountId: String) = booleanPreferencesKey("${accountId}_seenAccessKey")
        private val selectedAccountIdKey = stringPreferencesKey("selectedAccount")
        private fun entropyKey(accountId: String) = stringPreferencesKey("${accountId}_entropy")
        private fun userIdKey(entropy: String) = stringPreferencesKey("${entropy}_userId")
        private fun isUnregisteredKey(accountId: String) =
            booleanPreferencesKey("${accountId}_unregistered")
        private fun completedOnboardingKey(accountId: String) =
            booleanPreferencesKey("${accountId}_completedOnboarding")
    }

    private val credentialManager = CredentialManager.create(context)

    private val credentialLookupCache = mutableMapOf<String, PasswordCredential>()

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storage = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("credentials") }
    )

    suspend fun createAccount(): Result<String> {
        // Setup as new
        val seedB64 = Ed25519.createSeed16().encodeBase64()
        userManager.establish(seedB64)
        storage.edit { preferences ->
            preferences[temporaryEntropyKey] = seedB64
        }

        // Seed is retrieved internally via userManager state
        val backendResult = accountController.createAccount()
        if (backendResult.isFailure) {
            return Result.failure(
                Throwable(
                    backendResult.exceptionOrNull() ?: Throwable("Backend verification failed")
                )
            )
        }

        val userId = backendResult.getOrNull()

        if (userId == null) {
            return Result.failure(Throwable("No user id returned"))
        }

        storage.edit { preferences ->
            preferences[temporaryUserIdKey] = userId.base58
            preferences[completedOnboardingKey(userId.base58)] = false
        }

        updateUserManager(userId, AuthState.Onboarding(AuthState.ResumePoint.AccessKey))

        return Result.success(seedB64)
    }

    suspend fun onUserAccessKeySeen(): Result<Unit> {
        storage.edit { prefs ->
            val userId = prefs[temporaryUserIdKey] ?: prefs[selectedAccountIdKey]
            if (userId != null) {
                prefs[seenAccessKeyKey(userId)] = true
            }
        }

        return Result.success(Unit)
    }

    suspend fun hasSeenAccessKey(): Boolean {
        val tempUserId = storage.data.map { it[temporaryUserIdKey] }.firstOrNull()
        if (tempUserId != null) {
            return storage.data.map { it[seenAccessKeyKey(tempUserId)] }.firstOrNull() ?: false
        }
        // Temporary keys cleared by onAccountPurchased — check selected account.
        // Default to true so existing production users (who never had this flag) aren't affected.
        val selectedId = storage.data.map { it[selectedAccountIdKey] }.firstOrNull()
            ?: return true
        return storage.data.map { it[seenAccessKeyKey(selectedId)] }.firstOrNull() ?: true
    }

    suspend fun markOnboardingCompleted() {
        val accountId = storage.data.map { it[selectedAccountIdKey] }.firstOrNull()
            ?: storage.data.map { it[temporaryUserIdKey] }.firstOrNull()
            ?: return
        storage.edit { it[completedOnboardingKey(accountId)] = true }
    }

    suspend fun hasCompletedOnboarding(): Boolean {
        val accountId = storage.data.map { it[selectedAccountIdKey] }.firstOrNull()
            ?: storage.data.map { it[temporaryUserIdKey] }.firstOrNull()
            ?: return true
        // Default true for backward compat — existing users who upgraded never had this flag.
        return storage.data.map { it[completedOnboardingKey(accountId)] }.firstOrNull() ?: true
    }

    suspend fun presentSaveOption(): Result<AccountMetadata> {
        val tempUserId = storage.data.map { it[temporaryUserIdKey] }.firstOrNull()
        val entropy = storage.data.map { it[temporaryEntropyKey] }.firstOrNull()

        if (tempUserId == null) {
            return Result.failure(Throwable("No user id found"))
        }

        if (entropy == null) {
            return Result.failure(Throwable("No entropy found"))
        }

        val accountId = Base58.decode(tempUserId).toList()

        // Store credential
        storeCredential(entropy, accountId)

        // Store metadata
        val metadata = AccountMetadata.createFromId(accountId, entropy, isUnregistered = true)
        storeMetadata(metadata, isSelected = false)

        return Result.success(metadata)
    }

    suspend fun onAccountPurchased(): Result<AccountMetadata> {
        val tempUserId = storage.data.map { it[temporaryUserIdKey] }.firstOrNull()
        val entropy = storage.data.map { it[temporaryEntropyKey] }.firstOrNull().orEmpty()

        val accountId = runCatching { Base58.decode(tempUserId.orEmpty()).toList() }.getOrNull()

        if (accountId == null) {
            return Result.failure(Throwable("No user id found"))
        }

        // remove temporary states; persist seenAccessKey as false for the
        // selected account so a restart before the access-key screen resumes there
        val seenAccessKey = storage.data.map { it[seenAccessKeyKey(accountId.base58)] }.firstOrNull() ?: false
        storage.edit {
            it.remove(temporaryEntropyKey)
            it.remove(temporaryUserIdKey)
            it[seenAccessKeyKey(accountId.base58)] = seenAccessKey
        }

        // Store metadata
        val metadata = AccountMetadata.createFromId(accountId, entropy, isUnregistered = false)
        storeMetadata(metadata, isSelected = true)

        return Result.success(metadata)
    }

    suspend fun lookup(): LookupResult {
        val selectedAccountId =
            storage.data.map { it[selectedAccountIdKey] }.firstOrNull()

        val existingAccount = selectedAccountId?.let { id ->
            storage.data.map { it[entropyKey(id)] }.firstOrNull()
        }

        if (existingAccount != null) {
            return LookupResult.ExistingAccountFound(existingAccount)
        }

        val temporaryAccount = storage.data.map { it[temporaryEntropyKey] }.firstOrNull()
        if (temporaryAccount != null) {
            val entropy = storage.data.map { it[temporaryEntropyKey] }.firstOrNull()
            if (entropy != null) {
                val seenAccessKey =
                    storage.data.map { it[seenAccessKeyKey(temporaryAccount)] }.firstOrNull() ?: false
                val resumePoint = if (seenAccessKey) {
                    AuthState.ResumePoint.PostAccessKey
                } else {
                    AuthState.ResumePoint.AccessKey
                }
                return LookupResult.TemporaryAccountCreated(
                    entropy = entropy,
                    resumePoint = resumePoint
                )
            }
        }

        return LookupResult.NoAccountFound
    }

    suspend fun login(
        entropy: String,
        fromSelection: Boolean = false,
    ): Result<AccountMetadata> {
        userManager.establish(entropy)
        userManager.set(AuthState.Authenticating)

        val selectedMetadata = getSelectedMetadata()
        if (selectedMetadata != null && selectedMetadata.entropy == entropy) {
            storeMetadata(selectedMetadata, isSelected = true)
            userManager.set(selectedMetadata.id)
            return Result.success(selectedMetadata)
        }

        if (!fromSelection) {
            // Check existing credential
            val userId = getUserId(entropy)
            val existingCredential =
                credentialLookupCache[entropy] ?: getCredentialByEntropy(entropy, userId)

            if (existingCredential != null) {
                val metadata = getMetadata(userId.orEmpty())?.copy(isUnregistered = false)
                    ?: AccountMetadata(
                        userId.orEmpty(),
                        entropy,
                        isUnregistered = false
                    )

                storeMetadata(metadata, isSelected = true)

                credentialLookupCache.clear()

                return Result.success(metadata)
            }

            // Check fallback userId
            if (userId != null) {
                storeCredential(entropy, Base58.decode(userId).toList())
                storage.edit { it.remove(userIdKey(entropy)) }

                val metadata = AccountMetadata(userId, entropy, isUnregistered = false)
                storeMetadata(metadata, isSelected = true)
                return Result.success(metadata)
            }
        }

        // Non-existent credential - check with backend
        // Entropy is retrieved internally via userManager state
        val backendResult = accountController.login()
        if (backendResult.isFailure) return Result.failure(
            Throwable(
                backendResult.exceptionOrNull() ?: Throwable("Backend verification failed")
            )
        )

        val userIdBytes = backendResult.getOrNull()!!
        val userIdStr = userIdBytes.base58

        if (!fromSelection) {
            storeCredential(entropy, userIdBytes)
        }

        val metadata = AccountMetadata(userIdStr, entropy, isUnregistered = false)
        storeMetadata(metadata, isSelected = true)

        return Result.success(metadata)
    }

    suspend fun logout(): Result<Unit> {
        storage.edit {
            it.remove(selectedAccountIdKey)
            it.remove(temporaryUserIdKey)
            it.remove(temporaryEntropyKey)
        }

        return Result.success(Unit)
    }

    suspend fun selectCredential(): Result<MnemonicPhrase> {
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetPasswordOption())
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential as PasswordCredential
            val words = credential.password
                .replace(Regex("(\\s)+"), " ")
                .lowercase(Locale.getDefault()).split(" ")
            val mnemonic = MnemonicPhrase.newInstance(words)!!
            credentialLookupCache[mnemonic.wordString] = credential
            Result.success(mnemonic)
        } catch (e: Exception) {
            when (e) {
                is GetCredentialCancellationException -> Result.failure(SelectCredentialError.UserCancelled())
                else -> Result.failure(e)
            }
        }
    }

    private suspend fun storeMetadata(metadata: AccountMetadata, isSelected: Boolean) {
        storage.edit { preferences ->
            if (isSelected) {
                preferences[selectedAccountIdKey] = metadata._accountId
            }

            preferences[entropyKey(metadata._accountId)] = metadata.entropy

            preferences[isUnregisteredKey(metadata._accountId)] = metadata.isUnregistered
        }
    }

    private suspend fun getMetadata(accountId: String): AccountMetadata? {
        val preferences = storage.data.first()
        val entropy = preferences[entropyKey(accountId)] ?: return null
        val isUnregistered = preferences[isUnregisteredKey(accountId)] ?: false
        return AccountMetadata(accountId, entropy, isUnregistered)
    }

    private suspend fun storeUserId(entropy: String, userId: String) {
        storage.edit { preferences ->
            preferences[userIdKey(entropy)] = userId
        }
    }

    private suspend fun getUserId(entropy: String): String? {
        return storage.data.map { it[userIdKey(entropy)] }.firstOrNull()
    }

    private suspend fun getSelectedMetadata(): AccountMetadata? {
        val storedData = storage.data.firstOrNull()?.asMap()
        val emptyCheck = storedData.orEmpty().isEmpty()
        if (emptyCheck) return null

        if (storedData.orEmpty()[selectedAccountIdKey] == null) return null

        return storage.data
            .mapNotNull { preferences -> preferences[selectedAccountIdKey] }
            .map { accountId ->
                withContext(Dispatchers.IO) {
                    getMetadata(accountId)
                }
            }
            .firstOrNull()
    }

    private fun updateUserManager(userId: ID, state: AuthState) {
        userManager.set(userId)
        userManager.set(state)
    }

    // Retrieve credential by entropy using GetPasswordOption
    private suspend fun getCredentialByEntropy(
        entropy: String,
        expectedUserId: String? = null
    ): PasswordCredential? {
        if (!featureFlags.get(FeatureFlag.CredentialManager)) return null

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetPasswordOption(
                    allowedUserIds = setOf(mnemonicManager.fromEntropyBase64(entropy).toCredentialId()),
                    isAutoSelectAllowed = true
                )
            )
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val phrase = mnemonicManager.fromEntropyBase64(entropy).wordString
            val credential = result.credential
            if (credential is PasswordCredential && credential.id == expectedUserId && (credential.password == phrase)) {
                credential
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    }

    private suspend fun storeCredential(
        entropy: String,
        userId: ID,
        overwrite: Boolean = false,
    ): Result<Unit> {
        if (!featureFlags.get(FeatureFlag.CredentialManager)) return Result.failure(Throwable("Credential manager is force disabled"))

        val id = userId.base58
        val phrase = mnemonicManager.fromEntropyBase64(entropy)
        val credentialId = phrase.toCredentialId()

        if (!overwrite) {
            // Check for existing credential
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetPasswordOption(
                        allowedUserIds = setOf(credentialId),
                        isAutoSelectAllowed = true
                    )
                )
                .build()

            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is PasswordCredential && credential.id == credentialId && credential.password == phrase.wordString) {
                    // Credential exists and is valid; no need to recreate
                    return Result.success(Unit)
                }
            } catch (e: Exception) {
                // Credential not found or user canceled; proceed to create
            }
        }


        // Credential doesn't exist; create it
        val createRequest = CreatePasswordRequest(
            id = credentialId,
            password = phrase.wordString,
            isAutoSelectAllowed = true
        )
        return try {
            credentialManager.createCredential(context, createRequest)
            Result.success(Unit)
        } catch (e: Exception) {
            storeUserId(entropy, id)
            Result.success(Unit)
        }
    }

    private fun MnemonicPhrase.toCredentialId(): String {
        val sortedWords = words.sorted()
        val selectedWords = listOf(
            sortedWords[0],
            sortedWords[5],
            sortedWords[11]
        )
        return selectedWords.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }
    }
}

sealed class SelectCredentialError : Exception() {
    class UserCancelled : SelectCredentialError()
}