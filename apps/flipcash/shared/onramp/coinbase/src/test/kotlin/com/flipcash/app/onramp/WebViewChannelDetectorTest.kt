package com.flipcash.app.onramp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebViewChannelDetectorTest {

    // region null / missing inputs

    @Test
    fun `null package name returns null`() {
        assertNull(classifyWebViewChannel(null, null))
    }

    // endregion

    // region stable builds

    @Test
    fun `WEBVIEW_STABLE package digit returns null`() {
        // WEBVIEW_STABLE=0 → tens digit 0, abi=3 (arm64) → ...03
        assertNull(classifyWebViewChannel("com.google.android.webview", 782701303L))
    }

    @Test
    fun `TRICHROME package digit returns null`() {
        // TRICHROME=30 → tens digit 3, abi=3 → ...33
        assertNull(classifyWebViewChannel("com.android.chrome", 782701333L))
    }

    @Test
    fun `stable Chrome with null versionCode returns null`() {
        assertNull(classifyWebViewChannel("com.android.chrome", null))
    }

    @Test
    fun `stable android webview with arbitrary versionCode returns null`() {
        assertNull(classifyWebViewChannel("com.android.webview", 100L))
    }

    // endregion

    // region WEBVIEW_BETA (package digit 10 → tens digit 1)

    @Test
    fun `WEBVIEW_BETA on google webview returns Beta`() {
        // WEBVIEW_BETA=10 → tens digit 1, abi=3 → ...13
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.google.android.webview", 782701313L))
    }

    @Test
    fun `WEBVIEW_BETA on android chrome returns Beta`() {
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.android.chrome", 782701313L))
    }

    // endregion

    // region TRICHROME_BETA (package digit 40 → tens digit 4)

    @Test
    fun `TRICHROME_BETA on google webview returns Beta`() {
        // TRICHROME_BETA=40 → tens digit 4, abi=3 → ...43
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.google.android.webview", 782701343L))
    }

    @Test
    fun `TRICHROME_BETA on android chrome returns Beta`() {
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.android.chrome", 782701343L))
    }

    // endregion

    // region suffix-based classification

    @Test
    fun `beta suffix on google webview returns Beta`() {
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.google.android.webview.beta", 0L))
    }

    @Test
    fun `beta suffix on chrome returns Beta`() {
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.chrome.beta", 0L))
    }

    @Test
    fun `dev suffix on google webview returns Dev`() {
        assertEquals(WebViewChannel.Dev, classifyWebViewChannel("com.google.android.webview.dev", 0L))
    }

    @Test
    fun `dev suffix on chrome returns Dev`() {
        assertEquals(WebViewChannel.Dev, classifyWebViewChannel("com.chrome.dev", 0L))
    }

    @Test
    fun `canary suffix on google webview returns Canary`() {
        assertEquals(WebViewChannel.Canary, classifyWebViewChannel("com.google.android.webview.canary", 0L))
    }

    @Test
    fun `canary suffix on chrome returns Canary`() {
        assertEquals(WebViewChannel.Canary, classifyWebViewChannel("com.chrome.canary", 0L))
    }

    // endregion

    // region suffix takes precedence over versionCode

    @Test
    fun `beta suffix with WEBVIEW_BETA digit still returns Beta`() {
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.chrome.beta", 782701313L))
    }

    @Test
    fun `beta suffix with TRICHROME_BETA digit still returns Beta`() {
        assertEquals(WebViewChannel.Beta, classifyWebViewChannel("com.chrome.beta", 782701343L))
    }

    // endregion

    // region unknown vendors (fail-open)

    @Test
    fun `samsung webview returns null`() {
        assertNull(classifyWebViewChannel("com.samsung.android.webview", 0L))
    }

    @Test
    fun `huawei webview returns null`() {
        assertNull(classifyWebViewChannel("com.huawei.webview", 0L))
    }

    @Test
    fun `WEBVIEW_BETA digit on unknown vendor returns null`() {
        assertNull(classifyWebViewChannel("com.samsung.android.webview", 782701313L))
    }

    @Test
    fun `TRICHROME_BETA digit on unknown vendor returns null`() {
        assertNull(classifyWebViewChannel("com.samsung.android.webview", 782701343L))
    }

    // endregion

    // region false-positive guard

    @Test
    fun `random app with beta suffix returns null`() {
        assertNull(classifyWebViewChannel("com.some.random.app.beta", 0L))
    }

    // endregion
}
