package com.getcode.util

import androidx.camera.core.ImageProxy

fun ImageProxy.toByteArray(): ByteArray {
    val buffer = planes[0].buffer
    buffer.rewind()
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    return data
}