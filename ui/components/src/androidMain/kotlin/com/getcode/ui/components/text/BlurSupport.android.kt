package com.getcode.ui.components.text

import android.os.Build

internal actual val SupportsBlurEffect: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
