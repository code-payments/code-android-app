package com.flipcash.libs.currency.math

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Loads the curve tables from the source tree for a `@BeforeTest`, on every target.
 *
 * Node has no `runBlocking`, so this starts the suspend initializer directly and requires that it
 * finish without suspending. It does on every target here: the tables are read with blocking file
 * calls and the init lock is uncontended. If that ever changes, this fails rather than running the
 * test against an uninitialised curve.
 */
internal fun initializeCurveForTest() {
    var outcome: Result<Unit>? = null
    suspend { Curves.initializeFromSourceTree() }
        .startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    checkNotNull(outcome) { "curve initialisation suspended; it must complete synchronously in tests" }.getOrThrow()
}
