package com.flipcash.shared.chat.internal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.OwedRequestIntroStore
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.owedRequestIntrosDataStore: DataStore<Preferences> by preferencesDataStore(name = "owed_request_intros")

/** One string set of request ids in hex per account, as [DataStoreLobbyRequestStore] keys its own. */
@Singleton
internal class DataStoreOwedRequestIntroStore @Inject constructor(
    @ApplicationContext context: Context,
    private val userManager: UserManager,
) : OwedRequestIntroStore {

    private val dataStore = context.owedRequestIntrosDataStore

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun shown(): Set<ID> {
        val owner = userManager.accountId?.hexEncodedString() ?: return emptySet()
        return dataStore.data.first()[key(owner)].orEmpty()
            .mapNotNull { runCatching { it.hexToByteArray().toList() }.getOrNull() }
            .toSet()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun markShown(ids: Collection<ID>) {
        val owner = userManager.accountId?.hexEncodedString() ?: return
        dataStore.edit { prefs ->
            val key = key(owner)
            prefs[key] = prefs[key].orEmpty() + ids.map { it.toByteArray().toHexString() }
        }
    }

    private fun key(owner: String) = stringSetPreferencesKey("shown-$owner")
}
