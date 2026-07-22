package com.flipcash.app.core.navigation

enum class NavBarButton {
    Give,
    Wallet,
    Discover,
    Send,
    Tips,
    ;

    companion object {
        val defaultOrder = listOf(Discover, Give, Send, Tips, Wallet,)
    }
}
