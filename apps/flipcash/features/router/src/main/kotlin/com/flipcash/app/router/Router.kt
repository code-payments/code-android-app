package com.flipcash.app.router

import cafe.adriel.voyager.core.screen.Screen
import dev.theolm.rinku.DeepLink

interface Router {
    suspend fun processDestination(deeplink: DeepLink?): List<Screen>
    fun processType(deeplink: DeepLink?): DeeplinkType?
}

sealed interface DeeplinkType {
    data class Login(val entropy: String) : DeeplinkType
}