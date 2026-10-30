package com.flipcash.app.core.toast

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.opencode.model.financial.Fiat

interface ToastController {
    fun showToast(amount: Fiat, isDeposit: Boolean)
}

val LocalToastController = staticCompositionLocalOf<ToastController> {
    error("No ToastController provided")
}
