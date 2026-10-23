package com.flipcash.app.core.verification.email

import androidx.compose.runtime.staticCompositionLocalOf

val LocalEmailCodeChannel = staticCompositionLocalOf<EmailCodeChannel> {
    error("No EmailCodeChannel provided")
}
