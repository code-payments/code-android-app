package com.getcode.opencode.model.core

/** Reed-Solomon encodes a payload frame into the bytes a Kik code draws, or null if it can't. */
internal expect fun encodeScanCode(payload: ByteArray): ByteArray?
