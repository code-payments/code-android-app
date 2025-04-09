package com.flipcash.app.core

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.user.UserManager

val LocalUserManager = staticCompositionLocalOf<UserManager?> { null }
val LocalSessionController = staticCompositionLocalOf<SessionController?> { null }
