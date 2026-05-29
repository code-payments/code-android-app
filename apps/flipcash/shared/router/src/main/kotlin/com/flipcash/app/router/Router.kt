package com.flipcash.app.router

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import dev.theolm.rinku.DeepLink

interface Router {
    /** Parse + classify + resolve routes in one call. Called exactly once per deeplink. */
    fun dispatch(deepLink: DeepLink): DeeplinkAction

    /** Classify a URL (for QR code scanning). Does not resolve routes. */
    fun classify(deepLink: DeepLink): DeeplinkType?
}

val LocalRouter = staticCompositionLocalOf<Router?> { null }
