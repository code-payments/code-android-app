package com.flipcash.app.core.scanner

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSharedImageChannel = staticCompositionLocalOf<SharedImageChannel> {
    error("No SharedImageChannel provided")
}
