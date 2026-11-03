package com.flipcash.shared.chat.testing

import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/** `:libs:test-utils` is Android-only, so the common tests carry their own copy of this. */
class TestDispatchers(
    scheduler: TestCoroutineScheduler
) : DispatcherProvider {
    val dispatcher = StandardTestDispatcher(scheduler)

    override val IO: CoroutineDispatcher = dispatcher
    override val Main: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
}
