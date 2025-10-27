package com.kik.scan



class GroupKikCode private constructor(nativePtr: Long) : KikCode(nativePtr) {
    constructor(inviteCode: ByteArray, colourCode: Int) : this(
        createNative(inviteCode, colourCode)
    )

    constructor(colourCode: Int) : this(createNativeEmpty(colourCode))

    companion object {
        @JvmStatic
        private external fun createNative(inviteCode: ByteArray, colourCode: Int): Long
        @JvmStatic
        private external fun createNativeEmpty(colourCode: Int): Long
    }


    external override fun type(): Int // 3=Group
    external override fun colour(): Int
    external override fun encode(): ByteArray?
    external override fun destroyNative(ptr: Long)
    external fun decode(data: ByteArray)
    external fun inviteCode(): ByteArray
}