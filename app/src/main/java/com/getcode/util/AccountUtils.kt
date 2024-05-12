package com.getcode.util

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AuthenticatorException
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.ui.components.startupLog
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlin.coroutines.resume


object AccountUtils {
    private const val acctType = BuildConfig.APPLICATION_ID

    fun addAccount(context: Context, name: String, password: String, token: String) {
        val am: AccountManager = AccountManager.get(context)
        val a = Account(name, acctType)

        am.addAccountExplicitly(a, password, Bundle())
        am.setAuthToken(a, acctType, token)
    }

    suspend fun removeAccounts(context: Activity): @NonNull Single<Boolean> {
        return getAccount(context)
            .map {
                if (it.second == null) return@map false
                val am: AccountManager = AccountManager.get(context)
                am.removeAccountExplicitly(it.second)
            }
    }

    private suspend fun getAccount(context: Activity): @NonNull Single<Pair<String?, Account?>> {
        startupLog("getAccount")
        fun getAuthTokenByFeatures(cb: (k: String?, a: Account?) -> Unit) {
            val am: AccountManager = AccountManager.get(context)
            val start = Clock.System.now()
            am.getAuthTokenByFeatures(
                acctType, acctType, null, context, null, null,
                { future ->
                    try {
                        val bundle = future?.result
                        val authToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                        val accountName = bundle?.getString(AccountManager.KEY_ACCOUNT_NAME)
                        val account: Account? = getAccount(context, accountName)

                        val end = Clock.System.now()
                        startupLog("auth token feature fetch took ${end.toEpochMilliseconds() - start.toEpochMilliseconds()} ms")
                        startupLog("token=$authToken, $accountName, ${account?.name}")

                        cb(authToken.orEmpty(), account)

                        if (null == account && authToken != null) {
                            addAccount(context, accountName.orEmpty(), "", authToken)
                        }
                    } catch (e: AuthenticatorException) {
                        cb(null, null)
                    }
                }, null
            )
        }

        val subject = SingleSubject.create<Pair<String?, Account?>>()
        return subject.doOnSubscribe {
            CoroutineScope(Dispatchers.IO).launch {
                val result = getAccountNoActivity(context)
                subject.onSuccess(result ?: (null to null))
            }
        }
    }

    private suspend fun getAccountNoActivity(context: Context) : Pair<String?, Account?>? = suspendCancellableCoroutine {cont ->
        startupLog("getAuthToken")
        val am: AccountManager = AccountManager.get(context)
        val accountthing = am.accounts.getOrNull(0)
        if (accountthing == null) {
            cont.resume(null to null)
            return@suspendCancellableCoroutine
        }
        val start = Clock.System.now()
        am.getAuthToken(
            accountthing, acctType, null, false,
            { future ->
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val bundle = future?.result
                        val authToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                        val accountName = bundle?.getString(AccountManager.KEY_ACCOUNT_NAME)
                        val account: Account? = getAccount(context, accountName)

                        val end = Clock.System.now()
                        startupLog("auth token fetch took ${end.toEpochMilliseconds() - start.toEpochMilliseconds()} ms")

                        cont.resume(authToken.orEmpty() to  account)

                        if (null == account && authToken != null) {
                            addAccount(context, accountName.orEmpty(), "", authToken.orEmpty())
                        }
                    } catch (e: AuthenticatorException) {
                        cont.resume(null to null)
                    }
                }
            }, null
        )
    }


    suspend fun getToken(context: Context): String? {
        return getAccountNoActivity(context)?.first
    }

    suspend fun getToken(context: Activity): @NonNull Single<String> {
        return getAccount(context)
            .map { it.first.orEmpty() }
    }

    private fun getAccount(context: Context?, accountName: String?): Account? {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(acctType)
        for (account in accounts) {
            if (account.name.equals(accountName, ignoreCase = true)) {
                return account
            }
        }
        return null
    }
}