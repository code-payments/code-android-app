package com.flipcash.app.core.android.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

fun Uri.canNativelyHandle(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, this).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    val resolveInfos = context.packageManager.queryIntentActivities(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )

    return resolveInfos.any { resolveInfo ->
        val packageName = resolveInfo.activityInfo.packageName
        !isBrowser(context, packageName)
    }
}

private fun isBrowser(context: Context, packageName: String): Boolean {
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
    val browsers = context.packageManager.queryIntentActivities(
        browserIntent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    return browsers.any { it.activityInfo.packageName == packageName }
}