package com.flipcash.app.currencycreator.internal.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal class CurrencyCreatorTopBarController {
    var progress: Float by mutableFloatStateOf(0f)
    var onBack: () -> Unit by mutableStateOf({})
    var onEndAction: (() -> Unit)? by mutableStateOf(null)

    companion object {
        val LocalCurrencyCreatorTopBar = staticCompositionLocalOf<CurrencyCreatorTopBarController> {
            error("No CurrencyCreatorTopBarController provided")
        }
    }
}
