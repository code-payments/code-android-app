package com.flipcash.app.auth.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flipcash.services.user.UserManager
import com.getcode.utils.hexEncodedString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

private val Context.pushRegistrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "push_registration")

/**
 * One entry per account, `<result>|<source>|<epoch millis>`. Per account because one device serves
 * several, and a registration belongs to the account that made it.
 */
@Singleton
internal class DataStorePushRegistrationStore @Inject constructor(
    @ApplicationContext context: Context,
    private val userManager: UserManager,
) : PushRegistrationStore {

    private val dataStore = context.pushRegistrationDataStore

    override suspend fun read(): PushRegistration? {
        val owner = owner() ?: return null
        return dataStore.data.first()[key(owner)]?.let(::decode)
    }

    override suspend fun save(registration: PushRegistration) {
        val owner = owner() ?: return
        dataStore.edit { it[key(owner)] = encode(registration) }
    }

    override suspend fun clear() {
        val owner = owner() ?: return
        dataStore.edit { it.remove(key(owner)) }
    }

    private fun owner() = userManager.accountId?.hexEncodedString()

    private fun key(owner: String) = stringPreferencesKey("registration-$owner")

    internal companion object {
        fun encode(registration: PushRegistration): String =
            "${registration.result.key}|${registration.source.key}|${registration.at.toEpochMilliseconds()}"

        fun decode(raw: String): PushRegistration? {
            val parts = raw.split('|').takeIf { it.size == 3 } ?: return null
            val result = PushRegistrationResult.entries.firstOrNull { it.key == parts[0] } ?: return null
            val source = PushRegistrationSource.entries.firstOrNull { it.key == parts[1] } ?: return null
            val millis = parts[2].toLongOrNull() ?: return null
            return PushRegistration(Instant.fromEpochMilliseconds(millis), result, source)
        }
    }
}
