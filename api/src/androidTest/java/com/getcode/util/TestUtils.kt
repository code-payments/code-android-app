package com.getcode.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun testBlocking(block : suspend () -> Unit) {
    val continuation = Continuation<Unit>(EmptyCoroutineContext) {
        //Do nothing
        if (it.isFailure) {
            throw it.exceptionOrNull()!!
        }
    }
    block.startCoroutine(continuation)
}

fun runTest(block: suspend (scope : CoroutineScope) -> Unit) = runBlocking { block(this) }