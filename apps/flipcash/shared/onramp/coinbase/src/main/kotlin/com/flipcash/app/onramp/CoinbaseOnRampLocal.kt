package com.flipcash.app.onramp

import androidx.compose.runtime.staticCompositionLocalOf

val LocalCoinbaseOnRampController =
    staticCompositionLocalOf<CoinbaseOnRampController> { throw IllegalStateException() }
