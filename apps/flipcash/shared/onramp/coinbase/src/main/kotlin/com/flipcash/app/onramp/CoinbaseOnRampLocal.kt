package com.flipcash.app.onramp

import androidx.compose.runtime.compositionLocalOf

val LocalCoinbaseOnRampController =
    compositionLocalOf<CoinbaseOnRampController> { throw IllegalStateException() }
