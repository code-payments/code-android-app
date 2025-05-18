package com.getcode.util.resources

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.annotation.PluralsRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
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

    val displayMetrics: DisplayMetrics

    fun getDrawable(@DrawableRes drawableResId: Int): Drawable?

    fun getIdentifier(name: String, type: ResourceType): Int?

    fun getFont(@FontRes fontResId: Int): Typeface?

    fun getOfKinSuffix(): String
    fun getKinSuffix(): String
}

sealed interface ResourceType {
    val defType: kotlin.String
    data object Drawable: ResourceType {
        override val defType: kotlin.String = "drawable"
    }
    data object String: ResourceType {
        override val defType: kotlin.String = "string"
    }
}

val LocalResources = staticCompositionLocalOf<ResourceHelper> { NoOpResources }

private object NoOpResources : ResourceHelper {
    override fun getString(resourceId: Int): String = ""

    override fun getString(resourceId: Int, vararg formatArgs: Any): String = ""

    override fun getRawResource(resourceId: Int): String = ""

    override fun getQuantityString(
        id: Int,
        quantity: Int,
        vararg formatArgs: Any,
        default: String
    ): String = ""

    override fun getDimension(dimenId: Int, default: Float): Float = 0f

    override fun getDimensionPixelSize(dimenId: Int, default: Int): Int = 0

    override fun getDir(name: String, mode: Int): File? = null

    override val displayMetrics: DisplayMetrics
        get() = TODO("Not yet implemented")

    override fun getDrawable(drawableResId: Int): Drawable? = null

    override fun getIdentifier(name: String, type: ResourceType): Int? = null

    override fun getFont(fontResId: Int): Typeface? = null

    override fun getOfKinSuffix(): String = ""

    override fun getKinSuffix(): String = ""

}