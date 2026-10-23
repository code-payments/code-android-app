package com.flipcash.app.core.android.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

fun Uri.canNativelyHandle(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, this)

    if (Build.VERSION.SDK_INT >= 30) {
        val strictIntent = Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        }
        val pm = context.packageManager
        if (pm.resolveActivity(strictIntent, 0) != null) {
            return true
        }
    }

    // Fallback: classic query + filter out browsers
    val resolveInfos = context.packageManager.queryIntentActivities(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )

    return resolveInfos.any { it.activityInfo?.packageName?.let { pkg ->
        !isLikelyBrowserPackage(pkg)
    } == true }
}

private fun isLikelyBrowserPackage(pkg: String): Boolean = when (pkg) {
    "com.android.chrome",
    "com.google.android.chrome",
    "org.mozilla.firefox",
    "org.mozilla.firefox_beta",
    "com.opera.browser",
    "com.microsoft.emmx",
    "com.sec.android.app.sbrowser" -> true
    else -> false // extend as needed
}