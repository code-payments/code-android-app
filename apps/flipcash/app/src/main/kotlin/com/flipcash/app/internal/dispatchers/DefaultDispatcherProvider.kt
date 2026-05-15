package com.flipcash.app.internal.dispatchers

import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultDispatcherProvider : DispatcherProvider {
    override val Default: CoroutineDispatcher = Dispatchers.Default
    override val Main: CoroutineDispatcher = Dispatchers.Main
    override val IO: CoroutineDispatcher = Dispatchers.IO
}
