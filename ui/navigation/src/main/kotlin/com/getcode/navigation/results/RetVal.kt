package com.getcode.navigation.results

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass


/** Navigation result return value */
interface NavigationRetVal<T : Parcelable> : NavKey

inline fun <reified T : Parcelable> NavigationRetVal<T>.asKey(): NavResultKey<NavigationRetVal<T>, T> {
    // This uses the actual runtime class of 'this' while ensuring it matches the expected type
    return NavResultKey(
        this::class as KClass<NavigationRetVal<T>>,
        T::class
    )
}
