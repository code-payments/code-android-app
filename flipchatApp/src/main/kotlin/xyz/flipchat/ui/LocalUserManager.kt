package xyz.flipchat.ui

import androidx.compose.runtime.staticCompositionLocalOf
import xyz.flipchat.services.user.UserManager

val LocalUserManager = staticCompositionLocalOf<xyz.flipchat.services.user.UserManager?> { null }