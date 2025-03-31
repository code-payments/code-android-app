package com.getcode.services.utils

import io.reactivex.rxjava3.core.Completable

fun Completable.toKotlinResult(): Result<Unit> {
    return try {
        this.blockingAwait()
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }
}