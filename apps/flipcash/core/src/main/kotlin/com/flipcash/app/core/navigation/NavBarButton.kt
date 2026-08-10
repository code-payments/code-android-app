package com.flipcash.app.core.navigation

enum class NavBarButton {
    Give,
    Wallet,
    Discover,
    Tips,
    Chats,
    TipCard,
    Scanner,
    ;

    companion object {
        val defaultOrder = listOf(Discover, Give, Tips, Wallet,)
        val v2Order = listOf(Scanner, Wallet, Chats, TipCard)
    }
}
