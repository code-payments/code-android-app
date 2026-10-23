package com.getcode.ui.utils

import androidx.core.graphics.toColorInt

internal actual fun parseColorInt(hex: String): Int = hex.toColorInt()
