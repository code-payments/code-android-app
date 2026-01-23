package com.flipcash.app.core.data

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class Loadable<T> {
    data class Loading<T>(internal val data: T? = null) : Loadable<T>()
    data class Loaded<T>(val data: T) : Loadable<T>()
    data class Error<T>(
        val message: String?,
        val error: Throwable? = null,
        val retry: (() -> Unit)? = null,
    ) : Loadable<T>()

    val dataOrNull: T?
        get() = when (this) {
            is Loading -> data
            is Loaded -> data
            is Error -> null
        }
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T> Loadable<T>.isLoading(): Boolean {
    contract { returns(true) implies (this@isLoading is Loadable.Loading<T>) }
    return this is Loadable.Loading<T>
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T> Loadable<T>.isError(): Boolean {
    contract { returns(true) implies (this@isError is Loadable.Error<T>) }
    return this is Loadable.Error<T>
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T> Loadable<T>.isLoaded(): Boolean {
    contract { returns(true) implies (this@isLoaded is Loadable.Loaded<T>) }
    return this is Loadable.Loaded<T>
}