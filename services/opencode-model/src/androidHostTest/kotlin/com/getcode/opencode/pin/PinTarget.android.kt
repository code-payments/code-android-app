package com.getcode.opencode.pin

/** Android host tests: the host JDK, decimal places included. */
internal actual fun pinTarget(): PinTarget = hostPinTarget("host-jdk.txt")
