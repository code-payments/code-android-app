package com.getcode.util.resources

import androidx.annotation.DimenRes
import androidx.annotation.PluralsRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import java.io.File

interface ResourceHelper {
    fun getString(@StringRes resourceId: Int): String

    fun getString(@StringRes resourceId: Int, vararg formatArgs: Any): String

    fun getRawResource(@RawRes resourceId: Int): String

    fun getQuantityString(
        @PluralsRes id: Int,
        quantity: Int,
        vararg formatArgs: Any,
        default: String = "",
    ): String

    fun getDimension(@DimenRes dimenId: Int, default: Float = 0f): Float
    fun getDimensionPixelSize(@DimenRes dimenId: Int, default: Int = 0): Int

    fun getDir(name: String, mode: Int): File?
}