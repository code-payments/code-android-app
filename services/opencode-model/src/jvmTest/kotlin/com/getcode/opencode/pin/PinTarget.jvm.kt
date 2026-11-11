package com.getcode.opencode.pin

/** Desktop: the host JDK's formatting, with Android 16's decimal places. */
internal actual fun pinTarget(): PinTarget = hostPinTarget("desktop-jvm.txt")
