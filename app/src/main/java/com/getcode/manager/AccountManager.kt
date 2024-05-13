package com.getcode.manager

import android.content.Context
import com.getcode.util.AccountUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getToken(): String? {
        return AccountUtils.getToken(context)
    }
}