package com.flipcash.app.core.navigation

sealed interface DeeplinkAction {
    data class Navigate(val routes: List<com.flipcash.app.core.AppRoute>) : DeeplinkAction
    data class ExternalWallet(val type: DeeplinkType) : DeeplinkAction
    data class Login(val entropy: String) : DeeplinkAction
    data class OpenCashLink(val entropy: String) : DeeplinkAction
    data object None : DeeplinkAction
}
