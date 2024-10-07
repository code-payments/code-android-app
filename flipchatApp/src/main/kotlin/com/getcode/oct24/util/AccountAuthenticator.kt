package com.getcode.oct24.util


import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.NetworkErrorException
import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import com.getcode.utils.trace
import android.accounts.AccountManager as AndroidAccountManager


class AccountAuthenticator(
    private val context: Context,
) : AbstractAccountAuthenticator(context) {
    @Throws(NetworkErrorException::class)
    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String,
        requiredFeatures: Array<String>,
        options: Bundle
    ): Bundle = Bundle()

    @Throws(NetworkErrorException::class)
    override fun confirmCredentials(
        arg0: AccountAuthenticatorResponse,
        arg1: Account, arg2: Bundle
    ): Bundle? = null

    override fun editProperties(arg0: AccountAuthenticatorResponse, arg1: String): Bundle? = null

    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle
    ): Bundle {
        // Extract the username and password from the Account Manager, then, generate token
        val am = AndroidAccountManager.get(context)
        var authToken = am.peekAuthToken(account, authTokenType)
        trace("authenticator: authToken ${authToken != null}, $authTokenType")
        // Lets give another try to authenticate the user
        if (null != authToken) {
            if (authToken.isEmpty()) {
                val password = am.getPassword(account)
                if (password != null) {
                    authToken = "test"
                }
            }
        }

        // If we get an authToken - we return it
        if (null != authToken) {
            if (authToken.isNotEmpty()) {
                val result = Bundle()
                result.putString(AndroidAccountManager.KEY_ACCOUNT_NAME, account.name)
                result.putString(AndroidAccountManager.KEY_ACCOUNT_TYPE, account.type)
                result.putString(AndroidAccountManager.KEY_AUTHTOKEN, authToken)
                return result
            }
        }

        trace(
            message = "authenticator failure",
            error = Throwable("Failed to retrieve authToken from AccountManager")
        )
        // If we get here, then we couldn't access the user's password
        return Bundle()
    }

    override fun getAuthTokenLabel(arg0: String): String? {
        return "entropy"
    }

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(
        arg0: AccountAuthenticatorResponse, arg1: Account,
        arg2: Array<String>
    ): Bundle {
        // This call is used to query whether the Authenticator supports
        // specific features. We don't expect to get called, so we always
        // return false (no) for any queries.
        val result = bundleOf(AndroidAccountManager.KEY_BOOLEAN_RESULT to false)
        return result
    }

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(
        arg0: AccountAuthenticatorResponse,
        arg1: Account, arg2: String, arg3: Bundle
    ): Bundle? = null
}