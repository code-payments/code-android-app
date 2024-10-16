package com.getcode.navigation

import android.os.Parcelable
import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.model.TwitterUser
import kotlinx.parcelize.Parcelize

sealed class NavScreenProvider : ScreenProvider {
    sealed class Login {
        data class Home(val seed: String? = null): NavScreenProvider()
        data object SeedInput: NavScreenProvider()
        data class PhoneVerification(val loginArgs: LoginArgs = LoginArgs()): NavScreenProvider()
    }
    data class AppHomeScreen(val seed: String? = null,): NavScreenProvider()
    sealed class Chat {
        data object List : NavScreenProvider()
        data object ChatByUsername : NavScreenProvider()
        data class Conversation(
            val user: TwitterUser? = null,
            val chatId: com.getcode.model.ID? = null,
            val intentId: com.getcode.model.ID? = null
        ) : NavScreenProvider()
    }

    data object Balance : NavScreenProvider()
    data object Settings : NavScreenProvider()
}

@Parcelize
data class LoginArgs(
    val signInEntropy: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
    val phoneNumber: String? = null
): Parcelable