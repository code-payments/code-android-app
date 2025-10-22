package com.kik.scan

abstract class KikCode protected constructor(private var nativePtr: Long) {
    init {
        System.loadLibrary("kikCodes")
        System.loadLibrary("codeScanner")
    }

    abstract fun type(): Int
    abstract fun colour(): Int
    abstract fun encode(): ByteArray?

    protected fun finalize() {
        if (nativePtr != 0L) {
            destroyNative(nativePtr)
            nativePtr = 0L
        }
    }

    abstract fun destroyNative(ptr: Long)
}