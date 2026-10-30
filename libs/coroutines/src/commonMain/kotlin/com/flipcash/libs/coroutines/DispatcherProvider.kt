package com.flipcash.libs.coroutines

import kotlinx.coroutines.CoroutineDispatcher

@Suppress("PropertyName")
interface DispatcherProvider {
    val Default: CoroutineDispatcher
    val Main: CoroutineDispatcher
    val IO: CoroutineDispatcher
}