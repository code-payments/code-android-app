package com.flipcash.app.core.credentials

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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
) {
    companion object {
        private val selectedAccountIdKey = stringPreferencesKey("selectedAccount")
        private fun entropyKey(accountId: String) = stringPreferencesKey("${accountId}_entropy")
        private fun userIdKey(entropy: String) = stringPreferencesKey("${entropy}_userId")
        private fun isUnregisteredKey(accountId: String) =
            booleanPreferencesKey("${accountId}_unregistered")
    }

    private val credentialManager = CredentialManager.create(context)

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storage = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("credentials") }
    )

    suspend fun create(): Result<AccountMetadata> {
        // Setup as new
        val seedB64 = Ed25519.createSeed16().encodeBase64()
        userManager.establish(seedB64)

        // Seed is retrieved internally via userManager state
        val backendResult = accountController.createAccount()
        if (backendResult.isFailure) {
            return Result.failure(
                Throwable(
                    backendResult.exceptionOrNull() ?: Throwable("Backend verification failed")
                )
            )
        }

        val userId = backendResult.getOrNull()!!
        val entropy = userManager.entropy.orEmpty()

        // Store credential
        storeCredential(entropy, userId)

        // Store metadata
        println("storing metadata for ${userId.base58}")
        val metadata = AccountMetadata.createFromId(userId, entropy, isUnregistered = true)
        storeMetadata(metadata, isSelected = true)
        updateUserManager(userId, AuthState.Unregistered)

        return Result.success(metadata)
    }

    suspend fun lookup(): String? {
        val selectedAccountId = storage.data.map { it[selectedAccountIdKey] }.firstOrNull() ?: return null
        return storage.data.map { it[entropyKey(selectedAccountId)] }.firstOrNull()
    }

    suspend fun login(
        entropy: String,
    ): Result<AccountMetadata> {
        userManager.establish(entropy)
        userManager.set(AuthState.LoggedInAwaitingUser)

        val selectedMetadata = getSelectedMetadata()
        if (selectedMetadata != null) {
            updateUserManager(selectedMetadata.id, AuthState.LoggedIn)
            return Result.success(selectedMetadata)
        }

        // Check existing credential
        val userId = getUserId(entropy)
        val existingCredential = getCredentialByEntropy(entropy, userId)

        if (existingCredential != null) {
            val metadata = getMetadata(userId.orEmpty())?.copy(isUnregistered = false)
                ?: AccountMetadata(
                    userId.orEmpty(),
                    entropy,
                    isUnregistered = false
                )

            storeMetadata(metadata, isSelected = true)
            updateUserManager(
                Base58.decode(existingCredential.password).toList(),
                AuthState.LoggedIn
            )
            return Result.success(metadata)
        }

        // Check fallback userId
        if (userId != null) {
            storeCredential(entropy, Base58.decode(userId).toList())
            storage.edit { it.remove(userIdKey(entropy)) }

            val metadata = AccountMetadata(userId, entropy, isUnregistered = false)
            storeMetadata(metadata, isSelected = true)
            updateUserManager(Base58.decode(userId).toList(), AuthState.LoggedIn)
            return Result.success(metadata)
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
        storeCredential(entropy, userIdBytes)

        val metadata = AccountMetadata(userIdStr, entropy, isUnregistered = false)
        storeMetadata(metadata, isSelected = true)
        updateUserManager(userIdBytes, AuthState.LoggedIn)

        return Result.success(metadata)
    }

    suspend fun logout(): Result<Unit> {
        storage.edit {
            it.remove(selectedAccountIdKey)
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
            Result.success(mnemonic)
        } catch (e: Exception) {
            Result.failure(e)
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