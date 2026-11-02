package com.flipcash.shared.chat.ui

import android.os.Build

internal actual val canBlurRedactedText: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
