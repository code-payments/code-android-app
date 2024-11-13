package com.getcode.oct24.internal.preferences

import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.services.model.InternalRouting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class PreferenceStore @Inject constructor() {

    val db by lazy { FcAppDatabase.requireInstance() }

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

