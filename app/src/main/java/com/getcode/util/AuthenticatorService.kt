package com.getcode.util

import android.accounts.AccountManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class AuthenticatorService : Service() {
    @Inject
    lateinit var accountAuthenticator: AccountAuthenticator

    override fun onBind(intent: Intent): IBinder? {
        var binder: IBinder? = null
        if (intent.action == AccountManager.ACTION_AUTHENTICATOR_INTENT) {
            binder = accountAuthenticator.iBinder
        }
        return binder
    }
}