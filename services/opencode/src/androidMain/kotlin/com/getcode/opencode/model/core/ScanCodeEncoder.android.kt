package com.getcode.opencode.model.core

import com.kik.scan.Scanner

internal actual fun encodeScanCode(payload: ByteArray): ByteArray? = Scanner.encode(payload)
