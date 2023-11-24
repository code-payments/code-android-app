package com.getcode.util

import android.accounts.*
import android.content.Context
import android.os.Bundle


class AccountAuthenticator(private val mContext: Context) : AbstractAccountAuthenticator(mContext) {
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
        val am = AccountManager.get(mContext)
        var authToken = am.peekAuthToken(account, authTokenType)

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
                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
                result.putString(AccountManager.KEY_AUTHTOKEN, authToken)
                return result
            }
        }

        // If we get here, then we couldn't access the user's password
        return Bundle()
    }

    override fun getAuthTokenLabel(arg0: String): String? = null

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(
        arg0: AccountAuthenticatorResponse, arg1: Account,
        arg2: Array<String>
    ): Bundle? = null

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(
        arg0: AccountAuthenticatorResponse,
        arg1: Account, arg2: String, arg3: Bundle
    ): Bundle? = null
}