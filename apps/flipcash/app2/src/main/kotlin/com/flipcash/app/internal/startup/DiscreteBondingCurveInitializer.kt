package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.flipcash.libs.currency.math.Curves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiscreteBondingCurveInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            Curves.initialize(context)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}