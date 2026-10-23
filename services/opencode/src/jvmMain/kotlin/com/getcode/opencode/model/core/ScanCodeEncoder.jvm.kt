package com.getcode.opencode.model.core

// The encoder is the JNI library in vendor/kik/scanner, which has no desktop build yet.
internal actual fun encodeScanCode(payload: ByteArray): ByteArray? = null
