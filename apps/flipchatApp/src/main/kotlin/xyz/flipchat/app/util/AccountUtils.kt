package xyz.flipchat.app.util

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.core.os.bundleOf
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
import xyz.flipchat.app.BuildConfig
import java.util.Optional
import kotlin.coroutines.resume


object AccountUtils {
    private const val ACCOUNT_TYPE = BuildConfig.APPLICATION_ID
    private const val ACCOUNT_REGISTERED = "fc_account_registered"
    private const val ACCOUNT_UNREGISTERED = "fc_account_unregistered"

    fun addAccount(
        context: Context,
        name: String,
        password: String,
        token: String,
        isUnregistered: Boolean,
    ) {
        val accountManager: AccountManager = AccountManager.get(context)
        val account = Account(name, ACCOUNT_TYPE)

        val data = bundleOf(AccountManager.KEY_AUTH_TOKEN_LABEL to if (isUnregistered) ACCOUNT_UNREGISTERED else ACCOUNT_REGISTERED)
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

    suspend fun updateAccount(context: Context, name: String): Result<Unit> {
        return runCatching {
            val account = getAccount(context)
                .mapOptional {
                    Optional.ofNullable(it.second)
                }.blockingGet() ?: throw Throwable("Unable to get account")
            val am: AccountManager = AccountManager.get(context)

            suspendCancellableCoroutine { cont ->
                am.renameAccount(/* account = */ account,
                    /* newName = */ name,
                    /* callback = */ { future ->
                        try {
                            val bundle = future?.result
                            val updated = bundle?.name == name
                            if (updated) {
                                cont.resume(Unit)
                            } else {
                                cont.resumeWith(Result.failure(Throwable("Failed to update name")))
                            }
                        } catch (e: AuthenticatorException) {
                            e.printStackTrace()
                            cont.resumeWith(Result.failure(e))
                        }
                    },
                    /* handler = */ handler
                )
            }
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

    suspend fun getUserId(context: Context): UserIdResult? {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        fun getPassword(a: Account?): String? {
            if (a != null) {
                val pw = runCatching { accountManager.getPassword(a) }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                if (pw != null) {
                    return pw
                }
            }
            return null
        }

        val account = accounts.firstOrNull()

        val label = account?.let { accountManager.getUserData(it, AccountManager.KEY_AUTH_TOKEN_LABEL) }

        getPassword(account)?.let {
            if (label == ACCOUNT_UNREGISTERED) {
                return UserIdResult.Unregistered(it)
            } else {
                return UserIdResult.Registered(it)
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

        return getPassword(acct)?.let {
            if (label == ACCOUNT_UNREGISTERED) {
                UserIdResult.Unregistered(it)
            } else {
                UserIdResult.Registered(it)
            }
        }
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

    data class Account(override val token: String) : TokenResult
    data class Code(override val token: String) : TokenResult
}

sealed interface UserIdResult {
    data class Registered(val userId: String): UserIdResult
    data class Unregistered(val userId: String): UserIdResult
}