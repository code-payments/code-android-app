package com.flipcash.app.core.internal.accounts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getToken(type: String): String? {
        return AccountUtils.getToken(context, type)?.token
    }
}