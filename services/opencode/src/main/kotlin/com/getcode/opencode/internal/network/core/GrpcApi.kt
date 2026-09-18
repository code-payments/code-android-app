package com.getcode.opencode.internal.network.core

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.grpc.ManagedChannel

abstract class GrpcApi(protected val managedChannels: List<ManagedChannel>): DefaultLifecycleObserver {

    constructor(managedChannel: ManagedChannel): this(listOf(managedChannel))
    constructor(vararg managedChannels: ManagedChannel): this(managedChannels.toList())

    init {
        // LifecycleRegistry.addObserver rejects any thread but the main one, so registering here
        // directly would pin construction of every api -- and so of the whole object graph hanging off
        // them -- to the main thread. Posting keeps that off the startup path. addObserver replays the
        // current state, so a registration that lands after the process is already started still warms
        // the channel.
        val register = Runnable { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            register.run()
        } else {
            Handler(Looper.getMainLooper()).post(register)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        warmUp()
    }

    private fun warmUp() {
        // getState(true) requests a connection attempt if idle,
        // pre-connecting TCP + TLS + HTTP/2 in the background
        managedChannels.onEach { it.getState(true) }
    }
}
