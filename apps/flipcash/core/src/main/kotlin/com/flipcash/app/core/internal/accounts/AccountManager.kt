package com.flipcash.app.core.internal.accounts

import android.content.Context
import com.flipcash.app.core.AccountType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AccountType
    private val accountType: String
) {
    suspend fun getToken(): String? {
        return AccountUtils.getToken(context, accountType)?.token
    }
}