package com.flipcash.app.core.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

actual object AppProcessLifecycle {
    actual val owner: LifecycleOwner
        get() = ProcessLifecycleOwner.get()

    actual fun addObserver(observer: LifecycleObserver) {
        Handler(Looper.getMainLooper()).post {
            owner.lifecycle.addObserver(observer)
        }
    }
}
