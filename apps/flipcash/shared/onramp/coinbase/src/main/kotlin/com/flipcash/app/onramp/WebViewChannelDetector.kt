package com.flipcash.app.onramp

import android.content.Context
import androidx.webkit.WebViewCompat
import com.getcode.utils.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class WebViewChannel { Beta, Dev, Canary }

private val KNOWN_CHROMIUM_PREFIXES = listOf(
    "com.google.android.webview",
    "com.android.webview",
    "com.android.chrome",
    "com.chrome",
)

// Chromium's versionCode = (build * 1000 + patch) * 100 + packagePart + abiPart.
// The _PACKAGE_NAMES value is added directly, placing the package type in the
// tens digit and ABI in the ones digit of the last two digits.
// Relevant beta values:
//   WEBVIEW_BETA  = 10  → tens digit 1
//   TRICHROME_BETA = 40 → tens digit 4
// References:
//   https://chromium.googlesource.com/chromium/src/+/HEAD/build/util/android_chrome_version.py
//   https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/channels.md
private val BETA_PACKAGE_DIGITS = setOf(1L, 4L)

internal fun classifyWebViewChannel(
    packageName: String?,
    versionCode: Long?,
): WebViewChannel? {
    if (packageName == null) return null
    if (KNOWN_CHROMIUM_PREFIXES.none { packageName.startsWith(it) }) return null

    when {
        packageName.endsWith(".canary") -> return WebViewChannel.Canary
        packageName.endsWith(".dev") -> return WebViewChannel.Dev
        packageName.endsWith(".beta") -> return WebViewChannel.Beta
    }

    if (versionCode != null) {
        val packageDigit = (versionCode / 10L) % 10L
        if (packageDigit in BETA_PACKAGE_DIGITS) return WebViewChannel.Beta
    }

    return null
}

fun detectNonStableWebViewChannel(context: Context): WebViewChannel? {
    val pkg = WebViewCompat.getCurrentWebViewPackage(context)
    trace(
        tag = "CoinbaseOnRamp",
        message = "WebView provider lookup",
        metadata = {
            "packageName" to (pkg?.packageName ?: "null")
            "versionName" to (pkg?.versionName ?: "null")
            "longVersionCode" to (pkg?.longVersionCode?.toString() ?: "null")
        },
    )
    if (pkg == null) return null
    return classifyWebViewChannel(pkg.packageName, pkg.longVersionCode)
}

class WebViewChannelDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun detect(): WebViewChannel? = detectNonStableWebViewChannel(context)
}
