package com.getcode.opencode.internal.extensions

internal inline fun <T> Result<T>.filter(predicate: (T) -> Boolean): Result<T> {
    return when {
        isSuccess -> {
            val value = getOrThrow()
            if (predicate(value)) Result.success(value)
            else Result.failure(Exception("Value did not match predicate"))
        }
        else -> this // Propagate failure
    }
}

internal inline fun <reified K> Result<*>.filterIsInstance(): Result<K> {
    return when {
        isSuccess -> {
            val value = getOrThrow()
            if (value is K) Result.success(value)
            else {
                println(value?.javaClass?.simpleName)
                Result.failure(Exception("Value is not an instance of ${K::class.simpleName}"))
            }
        }
        else -> Result.failure(exceptionOrNull() ?: Exception("Unknown error"))
    }
}