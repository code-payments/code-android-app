package com.flipcash.app.core.navigation

enum class NavBarButton {
    Wallet,
    Chats,
    TipCard,
    Scanner,
    ;

    companion object {
        /** The fixed tab set, in bar order. Chats sits second, beside the scanner it now feeds. */
        val tabs = listOf(Scanner, Chats, Wallet, TipCard)
    }
}
