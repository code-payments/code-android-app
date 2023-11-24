package com.getcode.util

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AuthenticatorException
import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.getcode.BuildConfig
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine


object AccountUtils {
    private const val acctType = BuildConfig.APPLICATION_ID

    fun addAccount(context: Context, name: String, password: String, token: String) {
        val am: AccountManager = AccountManager.get(context)
        val a = Account(name, acctType)

        am.addAccountExplicitly(a, password, Bundle())
        am.setAuthToken(a, acctType, token)
    }

    fun removeAccounts(context: Activity): @NonNull Single<Boolean> {
        return getAccount(context)
            .map {
                if (it.second == null) return@map false
                val am: AccountManager = AccountManager.get(context)
                am.removeAccountExplicitly(it.second)
            }
    }

    private fun getAccount(context: Activity): @NonNull Single<Pair<String?, Account?>> {
        fun getAuthTokenByFeatures(cb: (k: String?, a: Account?) -> Unit) {
            val am: AccountManager = AccountManager.get(context)
            am.getAuthTokenByFeatures(
                acctType, acctType, null, context, null, null,
                { future ->
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val bundle = future?.result
                            val authToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                            val accountName = bundle?.getString(AccountManager.KEY_ACCOUNT_NAME)
                            val account: Account? = getAccount(context, accountName)

                            cb(authToken.orEmpty(), account)

                            if (null == account && authToken != null) {
                                addAccount(context, accountName.orEmpty(), "", authToken.orEmpty())
                            }
                        } catch (e: AuthenticatorException) {
                            cb(null, null)
                        }
                    }
                }, null
            )
        }

        val subject = SingleSubject.create<Pair<String?, Account?>>()
        return subject.doOnSubscribe {
            getAuthTokenByFeatures { s: String?, a: Account? ->
                subject.onSuccess(Pair(s, a))
            }
        }
    }

    private fun getAccountNoActivity(context: Context): Pair<String?, Account?>? {


            val am: AccountManager = AccountManager.get(context)
            val accountthing = am.accounts[0]

            am.getAuthToken(accountthing, acctType, null, false,
                { future ->
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val bundle = future?.result
                            val authToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                            val accountName = bundle?.getString(AccountManager.KEY_ACCOUNT_NAME)
                            val account: Account? = getAccount(context, accountName)

                            // return here    authToken.orEmpty(), account

                            if (null == account && authToken != null) {
                                addAccount(context, accountName.orEmpty(), "", authToken.orEmpty())
                            }
                        } catch (e: AuthenticatorException) {
                            // return here     null, null
                        }
                    }
                }, null
            )
            return null
        }


    fun getToken(context: Context): String? {
        return getAccountNoActivity(context)?.first
    }

    fun getToken(context: Activity): @NonNull Single<String> {
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