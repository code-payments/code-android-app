package com.getcode.opencode.internal.platform

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

internal actual fun observeProcessLifecycle(
    immediateOnMain: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onStart()
        override fun onStop(owner: LifecycleOwner) = onStop()
    }
    // LifecycleRegistry.addObserver rejects any thread but the main one.
    val register = Runnable { ProcessLifecycleOwner.get().lifecycle.addObserver(observer) }
    if (immediateOnMain && Looper.myLooper() == Looper.getMainLooper()) {
        register.run()
    } else {
        Handler(Looper.getMainLooper()).post(register)
    }
}
