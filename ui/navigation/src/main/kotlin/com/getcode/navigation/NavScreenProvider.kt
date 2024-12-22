package com.getcode.navigation

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.model.ID
import dev.theolm.rinku.DeepLink
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize

sealed class NavScreenProvider : ScreenProvider {


    sealed class Login {
        data class Home(val seed: String? = null) : NavScreenProvider()
        data object SeedInput : NavScreenProvider()
        data class NotificationPermission(val fromOnboarding: Boolean = false) : NavScreenProvider()
    }

    sealed interface CreateAccount {
        data object Start: NavScreenProvider()
        data class NameEntry(val showInModal: Boolean = false) : NavScreenProvider()
        data class AccessKey(val showInModal: Boolean = false) : NavScreenProvider()
        data object Purchase: NavScreenProvider()
    }

    data class AppHomeScreen(val deeplink: DeepLink? = null) : NavScreenProvider()
    sealed class Room {
        data object List : NavScreenProvider()
        sealed class Lookup {
            data object Entry : NavScreenProvider()
            data class Confirm(
                val args: RoomInfoArgs = RoomInfoArgs(),
                val returnToSender: Boolean = false
            ) : NavScreenProvider()
        }

        data class Messages(
            val chatId: ID? = null,
            val intentId: ID? = null
        ) : NavScreenProvider()

        data class Info(
            val args: RoomInfoArgs = RoomInfoArgs()
        ) : NavScreenProvider()

        data class ChangeCover(
            val id: ID
        ) : NavScreenProvider()

        data class ChangeName(
            val id: ID,
            val title: String,
        ): NavScreenProvider()
    }

    data object Balance : NavScreenProvider()
    data object Settings : NavScreenProvider()

    data object BetaFlags: NavScreenProvider()
}

@Parcelize
data class LoginArgs(
    val signInEntropy: String? = null,
    val isPhoneLinking: Boolean = false,
    val isNewAccount: Boolean = false,
    val phoneNumber: String? = null
) : Parcelable

@Parcelize
data class RoomInfoArgs(
    val roomId: ID? = null,
    val roomNumber: Long = 0,
    val roomTitle: String? = null,
    val memberCount: Int = 0,
    val ownerId: ID? = null,
    val hostName: String? = null,
    val coverChargeQuarks: Long = 0,
    val gradientColors: GradientColors = GradientColors(
        Triple(
            Color(0xFFFFBB00),
            Color(0xFF7306B7),
            Color(0xFF3E32C4),
        )
    )
) : Parcelable

@Parcelize
data class GradientColors(
    val triple: Triple<Color, Color, Color>
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        Triple(
            Color(parcel.readLong()),
            Color(parcel.readLong()),
            Color(parcel.readLong())
        )
    )

    companion object : Parceler<GradientColors> {
        override fun GradientColors.write(parcel: Parcel, flags: Int) {
            parcel.writeLong(triple.first.value.toLong())
            parcel.writeLong(triple.second.value.toLong())
            parcel.writeLong(triple.third.value.toLong())
        }

        override fun create(parcel: Parcel): GradientColors {
            return GradientColors(parcel)
        }
    }
}