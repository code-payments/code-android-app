package xyz.flipchat.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import xyz.flipchat.app.data.BetaFeatures
import xyz.flipchat.services.user.UserManager

val LocalUserManager = staticCompositionLocalOf<UserManager?> { null }
val LocalBetaFeatures = staticCompositionLocalOf { BetaFeatures.Default }