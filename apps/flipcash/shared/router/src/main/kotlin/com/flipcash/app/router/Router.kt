package com.flipcash.app.router

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.navigation.DeeplinkType
import dev.theolm.rinku.DeepLink

interface Router {
    suspend fun processDestination(deeplink: DeepLink?): List<NavKey>
    fun processType(deeplink: DeepLink?): DeeplinkType?
}

val LocalRouter = staticCompositionLocalOf<Router?> { null }
