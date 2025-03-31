package xyz.flipchat.internal.preferences

import com.getcode.services.model.InternalRouting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.flipchat.internal.db.FcAppDatabase
import javax.inject.Inject

internal class PreferenceStore @Inject constructor() {

    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    fun observe(pref: FcPref, default: Boolean): Flow<Boolean> {
        return db.prefBoolDao().observe(pref.key)
            .map { it?.value ?: default }
    }

    fun observe(pref: FcPref): Flow<Boolean?> {
        return db.prefBoolDao().observe(pref.key)
            .map { it?.value }
    }
}

sealed class FcPref(val key: String) {
    data object EligibleForAirdrop: FcPref("is_eligible_get_first_kin_airdrop"),
        InternalRouting
}

