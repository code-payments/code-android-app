package com.getcode.util

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

object ChromeTabsUtils {
    fun launchUrl(context: Context, url: String) {
        val mCustomTabsServiceConnection: CustomTabsServiceConnection?
        var mClient: CustomTabsClient?
        var mCustomTabsSession: CustomTabsSession? = null
        mCustomTabsServiceConnection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                componentName: ComponentName,
                customTabsClient: CustomTabsClient
            ) {
                mClient = customTabsClient
                mClient?.warmup(0L)
                mCustomTabsSession = mClient?.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mClient = null
            }
        }
        CustomTabsClient.bindCustomTabsService(
            context,
            "com.android.chrome",
            mCustomTabsServiceConnection
        )
        val customTabsIntent: CustomTabsIntent = CustomTabsIntent.Builder(mCustomTabsSession)
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}