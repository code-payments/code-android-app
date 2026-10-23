package com.flipcash.app.core.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun <T> Flow<Result<T>>.onResult(onError: (Throwable) -> Unit = { }, onSuccess: (T) -> Unit = { }): Flow<Result<T>> {
    return this.map {
        it.onSuccess(onSuccess).onFailure(onError)
    }
}

fun <T, R> Flow<Result<T>>.mapResult(block: suspend (T) -> R): Flow<Result<R>> {
    return this.map {
        if (it.isSuccess) {
            Result.success(block(it.getOrNull()!!))
        } else {
            Result.failure(it.exceptionOrNull() ?: Throwable("mapResult failed"))
        }
    }
}

fun <T, R> Flow<Result<T>>.flatMapResult(block: suspend (T) -> Result<R>): Flow<Result<R>> {
    return this.map {
        it.fold(
            onSuccess = { value -> block(value) },
            onFailure = { error -> Result.failure(error) }
        )
    }
}

fun <T> Flow<Result<T>>.onError(block: (Throwable) -> Unit): Flow<Result<T>> {
    return this.map {
        it.onFailure(block)
    }
}