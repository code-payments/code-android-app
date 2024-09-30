package com.getcode.ui.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import java.util.*
import kotlin.concurrent.timerTask


fun Int.dipToPixels() = (Resources.getSystem().displayMetrics.density * this).toInt()

val Int.nonScaledSp
    @Composable
    get() = (this / LocalDensity.current.fontScale).sp

fun Context.getActivity(): FragmentActivity? = when (this) {
    is AppCompatActivity -> this
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

fun androidx.compose.ui.graphics.Color.toAGColor() = toArgb().run {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){
        android.graphics.Color.argb(alpha, red, green, blue)
    } else {
        android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}

val Float.toPx get() = this * Resources.getSystem().displayMetrics.density
val Float.toDp get() = this / Resources.getSystem().displayMetrics.density

val Int.toPx get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp get() = (this / Resources.getSystem().displayMetrics.density).toInt()

fun withDelay(delay: Long, block: () -> Unit) {
    Timer().schedule(timerTask { block() } , delay)
}