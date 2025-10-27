package com.kik.scan


class RemoteKikCode private constructor(nativePtr: Long) : KikCode(nativePtr) {
    constructor(payload: ByteArray, colourCode: Int) : this(
        createNative(payload, colourCode)
    )

    constructor(colourCode: Int) : this(createNativeEmpty(colourCode))

    companion object {
        @JvmStatic
        private external fun createNative(payload: ByteArray, colourCode: Int): Long
        @JvmStatic
        private external fun createNativeEmpty(colourCode: Int): Long
    }

    external override fun type(): Int // 2=Remote
    external override fun colour(): Int
    external override fun encode(): ByteArray?
    external override fun destroyNative(ptr: Long)
    external fun decode(data: ByteArray)
    external fun payload(): ByteArray
}