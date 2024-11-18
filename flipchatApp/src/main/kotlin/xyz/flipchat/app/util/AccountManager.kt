package xyz.flipchat.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getToken(): String? {
        return AccountUtils.getToken(context)?.token
    }
}