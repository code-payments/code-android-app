package com.flipcash.shared.chat.internal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ApproveNewMembersStore
import com.getcode.utils.hexEncodedString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.approveNewMembersDataStore: DataStore<Preferences> by preferencesDataStore(name = "approve_new_members")

/**
 * One string set per account of the chat ids (hex) whose switch is on. Per account, as
 * [DataStoreLobbyRequestStore] is, because one device serves several.
 */
@Singleton
internal class DataStoreApproveNewMembersStore(
    private val dataStore: DataStore<Preferences>,
    /** The signed-in account as hex, null when signed out. */
    private val owners: Flow<String?>,
    private val currentOwner: () -> String?,
) : ApproveNewMembersStore {

    @Inject
    constructor(@ApplicationContext context: Context, userManager: UserManager) : this(
        dataStore = context.approveNewMembersDataStore,
        owners = userManager.state.map { it.accountId?.hexEncodedString() },
        currentOwner = { userManager.accountId?.hexEncodedString() },
    )

    @OptIn(ExperimentalStdlibApi::class)
    override fun observe(chatId: ChatId): Flow<Boolean> =
        combine(
            owners.distinctUntilChanged(),
            dataStore.data,
        ) { owner, prefs ->
            owner != null && chatId.bytes.toHexString() in prefs[key(owner)].orEmpty()
        }.distinctUntilChanged()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun set(chatId: ChatId, on: Boolean) {
        val owner = currentOwner() ?: return
        val hex = chatId.bytes.toHexString()
        dataStore.edit { prefs ->
            val key = key(owner)
            val current = prefs[key].orEmpty()
            prefs[key] = if (on) current + hex else current - hex
        }
    }

    private fun key(owner: String) = stringSetPreferencesKey("on-$owner")
}
