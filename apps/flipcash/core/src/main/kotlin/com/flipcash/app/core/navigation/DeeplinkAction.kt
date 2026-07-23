package com.flipcash.app.core.navigation

import com.getcode.opencode.model.core.ID

sealed interface DeeplinkAction {
    data class Navigate(val routes: List<com.flipcash.app.core.AppRoute>) : DeeplinkAction
    data class Login(val entropy: String) : DeeplinkAction
    data class OpenCashLink(val entropy: String) : DeeplinkAction
    data class PresentTipCard(val userId: ID): DeeplinkAction
    data object None : DeeplinkAction
}
