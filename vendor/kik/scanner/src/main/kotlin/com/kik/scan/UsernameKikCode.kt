package com.kik.scan

class UsernameKikCode private constructor(nativePtr: Long) : KikCode(nativePtr) {
    constructor(username: String, nonce: Int, colourCode: Int) : this(
        createNative(username, nonce, colourCode)
    )

    constructor(colourCode: Int) : this(createNativeEmpty(colourCode))

    companion object {
        @JvmStatic
        private external fun createNative(username: String, nonce: Int, colourCode: Int): Long
        @JvmStatic
        private external fun createNativeEmpty(colourCode: Int): Long
    }

    external override fun type(): Int // 1=Username
    external override fun colour(): Int
    external override fun encode(): ByteArray?
    external override fun destroyNative(ptr: Long)
    external fun decode(data: ByteArray)
    external fun username(): String
    external fun nonce(): Int
}