package com.flipcash.app.core.navigation

enum class NavBarButton {
    Give,
    Wallet,
    Discover,
    Tips,
    ;

    companion object {
        val defaultOrder = listOf(Discover, Give, Tips, Wallet,)
    }
}
