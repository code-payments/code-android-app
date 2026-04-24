package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import com.flipcash.libs.currency.math.loader.FileTableLoader
import kotlinx.coroutines.runBlocking

object CurveTestInitializer {
    fun initialize() {
        runBlocking { DiscreteBondingCurve.initialize(FileTableLoader()) }
    }
}
