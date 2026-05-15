package com.flipcash.app.onramp

import androidx.compose.runtime.compositionLocalOf

val LocalExternalWalletOnRampController =
    compositionLocalOf<ExternalWalletOnRampController> { throw IllegalStateException() }
