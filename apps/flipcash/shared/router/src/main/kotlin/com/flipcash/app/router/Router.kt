package com.flipcash.app.router

import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.core.navigation.DeeplinkType
import dev.theolm.rinku.DeepLink

interface Router {
    suspend fun processDestination(deeplink: DeepLink?): List<Screen>
    fun processType(deeplink: DeepLink?): DeeplinkType?
}

val LocalRouter = staticCompositionLocalOf<Router?> { null }