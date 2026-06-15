package com.flipcash.app.core.toast

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

interface SystemToastController {
    fun showToast(message: String, replacePrevious: Boolean = false)
    fun showToast(@StringRes messageRes: Int, vararg args: Any, replacePrevious: Boolean = false)
    fun showQuantityToast(@PluralsRes pluralRes: Int, quantity: Int, vararg args: Any, replacePrevious: Boolean = false)
}
