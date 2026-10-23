package com.flipcash.libs.currency.math

import kotlinx.coroutines.runBlocking

object CurveTestInitializer {
    fun initialize() {
        runBlocking { Curves.initializeFromSourceTree() }
    }
}
