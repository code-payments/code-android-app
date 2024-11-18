package com.getcode.navigation

import android.os.Parcelable
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.model.ID
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
            data class Confirm(val args: RoomInfoArgs = RoomInfoArgs()): NavScreenProvider()
        }
        data class Conversation(
            val chatId: ID? = null,
            val intentId: ID? = null
        ) : NavScreenProvider()

        data class Info(
            val args: RoomInfoArgs = RoomInfoArgs()
        ): NavScreenProvider()
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

@Parcelize
data class RoomInfoArgs(
    val roomId: ID? = null,
    val roomNumber: Long = 0,
    val roomTitle: String? = null,
    val memberCount: Int = 0,
    val hostId: ID? = null,
    val hostName: String? = null,
    val coverChargeQuarks: Long = 0,
    val gradientColors: Triple<Color, Color, Color> = Triple(
        Color(0xFFFFBB00),
        Color(0xFF7306B7),
        Color(0xFF3E32C4),
    )
): Parcelable