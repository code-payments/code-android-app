package com.getcode.oct24.util

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.core.os.bundleOf
import com.getcode.model.ID
import com.getcode.oct24.BuildConfig
import com.getcode.utils.TraceType
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlin.coroutines.resume


object AccountUtils {
    private const val ACCOUNT_TYPE = BuildConfig.APPLICATION_ID

    fun addAccount(
        context: Context,
        name: String,
        password: String,
        token: String
    ) {
        val accountManager: AccountManager = AccountManager.get(context)
        val account = Account(name, ACCOUNT_TYPE)

        val data = bundleOf(AccountManager.KEY_AUTH_TOKEN_LABEL to "entropy")
        accountManager.addAccountExplicitly(account, password, data)
        accountManager.setAuthToken(account, ACCOUNT_TYPE, token)
    }

    suspend fun removeAccounts(context: Context): @NonNull Single<Boolean> {
        return getAccount(context)
            .map {
                if (it.second == null) return@map false
                val am: AccountManager = AccountManager.get(context)
                am.removeAccountExplicitly(it.second)
            }
    }

    private suspend fun getAccount(context: Context): @NonNull Single<Pair<String?, Account?>> {
        val subject = SingleSubject.create<Pair<String?, Account?>>()
        return subject.doOnSubscribe {
            CoroutineScope(Dispatchers.IO).launch {
                val result = retryable(
                    call = { getAccountNoActivity(context) },
                    onRetry = { currentAttempt ->
                        trace(
                            tag = "Account",
                            message = "Retrying call",
                            metadata = {
                                "count" to currentAttempt
                            },
                            type = TraceType.Process,
                        )
                    }
                )
                subject.onSuccess(result ?: (null to null))
            }
        }
    }

    private val handler: Handler by lazy { Handler(handlerThread.looper) }

    private val handlerThread: HandlerThread by lazy {
        HandlerThread("RenetikBackgroundThread").apply {
            setUncaughtExceptionHandler { _, e -> run { throw RuntimeException(e) } }
            start()
        }
    }

    private suspend fun getAccountNoActivity(
        context: Context
    ): Pair<String?, Account?>? = suspendCancellableCoroutine { cont ->
        trace("getAuthToken", type = TraceType.Silent)
        val am: AccountManager = AccountManager.get(context)
        val account = am.getAccountsByType(ACCOUNT_TYPE).firstOrNull()
        if (account == null) {
            trace(
                "no associated account found",
                type = TraceType.Error
            )
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val start = Clock.System.now()
        am.getAuthToken(
            account, ACCOUNT_TYPE, null, false,
            { future ->
                try {
                    val bundle = future?.result
                    val authToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)

                    val end = Clock.System.now()
                    trace("auth token fetch took ${end.toEpochMilliseconds() - start.toEpochMilliseconds()} ms")

                    cont.resume(authToken.orEmpty() to account)
                } catch (e: AuthenticatorException) {
                    trace(
                        message = "failed to read account",
                        error = e,
                        type = TraceType.Error
                    )
                    cont.resume(null)
                }
            }, handler
        )
    }

    suspend fun getUserId(context: Context): String? {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        val account = accounts.firstOrNull()
        if (account != null) {
            val pw = runCatching { accountManager.getPassword(account) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
            if (pw != null) {
                return pw
            }
        }

        val (_, acct) = retryable(
            call = { getAccountNoActivity(context) },
            onRetry = { currentAttempt ->
                trace(
                    tag = "Account",
                    message = "Retrying call",
                    metadata = {
                        "count" to currentAttempt
                    },
                    type = TraceType.Process,
                )
            }
        ) ?: return null

        if (acct != null) {
            val pw = runCatching { accountManager.getPassword(acct) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
            if (pw != null) {
                return pw
            }
        }

        return null
    }

    suspend fun getToken(context: Context): TokenResult? {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        val account = accounts.firstOrNull()
        if (account != null) {
            val token = runCatching { accountManager.peekAuthToken(account, ACCOUNT_TYPE) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
            if (token != null) {
                return TokenResult.Account(token)
            }
        }

        val token = retryable(
            call = { getAccountNoActivity(context) },
            onRetry = { currentAttempt ->
                trace(
                    tag = "Account",
                    message = "Retrying call",
                    metadata = {
                        "count" to currentAttempt
                    },
                    type = TraceType.Process,
                )
            }

        )?.first ?: return null

        return TokenResult.Account(token)
    }
}

sealed interface TokenResult {
    val token: String
    data class Account(override val token: String): TokenResult
    data class Code(override val token: String): TokenResult
}