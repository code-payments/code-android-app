package xyz.flipchat.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import xyz.flipchat.app.beta.BetaFlags
import xyz.flipchat.app.beta.NoOpBetaFlags
import xyz.flipchat.services.user.UserManager

val LocalUserManager = staticCompositionLocalOf<UserManager?> { null }
val LocalBetaFeatures = staticCompositionLocalOf<BetaFlags> { NoOpBetaFlags }