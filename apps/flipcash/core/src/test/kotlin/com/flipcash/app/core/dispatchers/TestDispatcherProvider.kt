package com.flipcash.app.core.dispatchers

import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

class TestDispatcherProvider(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : DispatcherProvider {
    override val Default: CoroutineDispatcher get() = testDispatcher
    override val Main: CoroutineDispatcher get() = testDispatcher
    override val IO: CoroutineDispatcher get() = testDispatcher
}
