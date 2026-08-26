package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.flipcash.libs.currency.math.Curves

/**
 * Preloads the discrete bonding curve tables.
 *
 * Depends on [TraceInitializer] so a failure here is actually traceable.
 */
class DiscreteBondingCurveInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        launchBestEffort(tag = "curves") {
            Curves.initialize(context)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return listOf(TraceInitializer::class.java)
    }
}
