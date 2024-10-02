package com.getcode.navigation

import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.model.ID
import com.getcode.model.TwitterUser

sealed class NavScreenProvider : ScreenProvider {
    sealed class Chat {
        data object List : NavScreenProvider()
        data object ChatByUsername : NavScreenProvider()
        data class Conversation(
            val user: TwitterUser? = null,
            val chatId: ID? = null,
            val intentId: ID? = null
        ) : NavScreenProvider()
    }

    data object Balance : NavScreenProvider()
    data object Settings : NavScreenProvider()
}