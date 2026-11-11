package com.getcode.crypt

/**
 * JS uses [PBKDF2SHA512Reference], as Apple does. Node's `crypto.pbkdf2Sync` would be faster, but
 * nothing on JS derives a seed yet, and the reference keeps the browser path free of Node APIs.
 */
internal actual fun derivePlatform(P: String, S: String, c: Int, dkLen: Int): ByteArray? = null
