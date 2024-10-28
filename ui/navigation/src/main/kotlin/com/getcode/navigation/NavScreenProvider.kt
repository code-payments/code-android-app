package com.getcode.navigation

import android.os.Parcelable
import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.model.ID
import com.getcode.model.TwitterUser
import kotlinx.parcelize.Parcelize

sealed class NavScreenProvider : ScreenProvider {
    data object Registration: NavScreenProvider()
    data class NotificationPermission(val fromOnboarding: Boolean = false): NavScreenProvider()

    sealed class Login {
        data class Home(val seed: String? = null): NavScreenProvider()
        data object SeedInput: NavScreenProvider()
        data class PhoneVerification(val loginArgs: LoginArgs = LoginArgs()): NavScreenProvider()
    }
    data class AppHomeScreen(val seed: String? = null,): NavScreenProvider()
    sealed class Chat {
        data object List : NavScreenProvider()
        sealed class Lookup {
            data object Entry : NavScreenProvider()
            data class Confirm(val id: ID): NavScreenProvider()
        }
        data class Conversation(
            val chatId: ID? = null,
            val intentId: ID? = null
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