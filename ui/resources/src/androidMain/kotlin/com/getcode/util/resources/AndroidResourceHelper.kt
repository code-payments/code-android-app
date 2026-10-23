package com.getcode.util.resources

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import java.io.File

/** The [ResourceHelper] members that return Android types. */
interface AndroidResourceHelper : ResourceHelper {
    fun getDir(name: String, mode: Int): File?

    val displayMetrics: DisplayMetrics

    fun getDrawable(@DrawableRes drawableResId: Int): Drawable?

    fun getFont(@FontRes fontResId: Int): Typeface?
}
