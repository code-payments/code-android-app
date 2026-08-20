package com.flipcash.app.core.navigation

enum class NavBarButton {
    Wallet,
    Chats,
    TipCard,
    Scanner,
    ;

    companion object {
        /** The fixed tab set, in bar order. */
        val tabs = listOf(Scanner, Wallet, Chats, TipCard)
    }
}
