package com.getcode.services.utils

suspend fun <T, R> Result<T>.mapResult(transform: suspend (T) -> Result<R>): Result<R> {
    return try {
        this.fold(
            onSuccess = { value -> transform(value) },
            onFailure = { error -> Result.failure(error) }
        )
    } catch (e: Throwable) {
        Result.failure(e)
    }
}