package com.flipcash.app.core.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

actual object AppProcessLifecycle {
    actual val owner: LifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = registry
    }

    // A desktop window has no background state the app needs to react to yet, so the process
    // stays RESUMED. Window minimise/close can drive this registry once something needs it.
    private val registry = LifecycleRegistry.createUnsafe(owner).apply {
        currentState = Lifecycle.State.RESUMED
    }

    actual fun addObserver(observer: LifecycleObserver) {
        synchronized(registry) { registry.addObserver(observer) }
    }
}
