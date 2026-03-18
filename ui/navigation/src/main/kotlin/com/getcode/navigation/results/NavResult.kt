package com.getcode.navigation.results

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 *  NavResultStore value
 */
sealed interface NavResultOrCanceled<out T : Parcelable> : Parcelable {
    @Parcelize
    data class ReturnValue<T : Parcelable>(val value: T) : NavResultOrCanceled<T>

    @Parcelize
    data object Canceled : NavResultOrCanceled<Nothing>
}

/**
 * Convenience to get the value or null if canceled
 */
fun <T : Parcelable> NavResultOrCanceled<T>.getOrNull(): T? =
    when (this) {
        is NavResultOrCanceled.ReturnValue -> value
        NavResultOrCanceled.Canceled -> null
    }

/**
 * Invoke the block if we have a value (not canceled)
 *
 * A slight convenience over `getOrNull()?.let { value -> block(value) }`. Worthwhile?
 */
inline fun <T : Parcelable> NavResultOrCanceled<T>.ifValue(block: (T) -> Unit) {
    if (this is NavResultOrCanceled.ReturnValue) block(value)
}
