package com.flipcash.app.core.navigation

enum class NavBarButton {
    Give,
    Wallet,
    Discover,
    ;

    companion object {
        val defaultOrder = listOf(Give, Wallet, Discover)
    }
}
