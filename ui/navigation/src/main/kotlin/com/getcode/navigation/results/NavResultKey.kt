package com.getcode.navigation.results

import android.os.Parcelable
import com.getcode.navigation.utils.KClassSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * NavResultStore key
 *
 * Keys are based on the result generating NavKey type and the return type.
 */
@Serializable
data class NavResultKey<out K : NavigationRetVal<out T>, out T : Parcelable>(
    @Serializable(with = KClassSerializer::class)
    val routeClass: KClass<out K>,
    @Serializable(with = KClassSerializer::class)
    val valueClass: KClass<out T>,
) {
    val name: String get() = toString()
}

inline fun <reified K : NavigationRetVal<out T>, reified T : Parcelable> key(): NavResultKey<K, T> =
    NavResultKey(K::class, T::class)
