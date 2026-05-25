package com.flipcash.app.core.navigation

import androidx.annotation.StringRes
import com.flipcash.core.R

enum class GiveButtonLabel(@StringRes val labelRes: Int) {
    Give(R.string.action_give),
    Cash(R.string.action_cash),
}
