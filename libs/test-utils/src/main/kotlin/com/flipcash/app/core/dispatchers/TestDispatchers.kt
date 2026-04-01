package com.flipcash.app.core.dispatchers

import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher

class TestDispatchers(
    scheduler: TestCoroutineScheduler
) : DispatcherProvider {
    val dispatcher = StandardTestDispatcher(scheduler)

    override val IO: CoroutineDispatcher = dispatcher
    override val Main: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
}
