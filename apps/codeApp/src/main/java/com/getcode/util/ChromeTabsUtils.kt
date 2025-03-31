package com.getcode.util

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.ui.utils.toAGColor

object ChromeTabsUtils {
    fun launchUrl(
        context: Context,
        url: String,
        showBack: Boolean = false
    ) {
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
        val builder: CustomTabsIntent.Builder = CustomTabsIntent.Builder(mCustomTabsSession)
            .setShowTitle(false)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setInstantAppsEnabled(false)
            .setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK,
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(Brand.toAGColor())
                    .setNavigationBarDividerColor(Color.Transparent.toAGColor())
                    .setNavigationBarColor(Color.Transparent.toAGColor())
                    .build()
            )

        if (showBack) {
            ContextCompat.getDrawable(
                context,
                R.drawable.ic_arrow_back
            )?.toBitmap()?.let { backArrow ->
                builder.setCloseButtonIcon(backArrow)
            }
        }

        val customTabsIntent = builder.build()

        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}