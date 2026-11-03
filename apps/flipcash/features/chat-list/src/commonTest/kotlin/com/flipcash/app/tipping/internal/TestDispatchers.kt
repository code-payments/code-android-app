package com.flipcash.app.tipping.internal

import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

internal class TestDispatchers(scheduler: TestCoroutineScheduler) : DispatcherProvider {
    private val dispatcher = StandardTestDispatcher(scheduler)

    override val IO: CoroutineDispatcher = dispatcher
    override val Main: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
}
