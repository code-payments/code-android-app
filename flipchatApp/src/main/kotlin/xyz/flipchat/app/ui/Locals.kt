package xyz.flipchat.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.app.beta.NoOpLabs
import xyz.flipchat.services.user.UserManager

val LocalUserManager = staticCompositionLocalOf<UserManager?> { null }
val LocalLabs = staticCompositionLocalOf<Labs> { NoOpLabs }