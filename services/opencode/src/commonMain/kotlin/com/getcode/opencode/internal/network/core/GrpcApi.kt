package com.getcode.opencode.internal.network.core

import com.getcode.opencode.internal.platform.observeProcessLifecycle
import io.grpc.ManagedChannel

abstract class GrpcApi(protected val managedChannels: List<ManagedChannel>) {

    constructor(managedChannel: ManagedChannel): this(listOf(managedChannel))
    constructor(vararg managedChannels: ManagedChannel): this(managedChannels.toList())

    init {
        // Registering on the constructing thread would pin construction of every api -- and so of the whole object graph hanging off
        // them -- to the main thread. Posting keeps that off the startup path. addObserver replays the
        // current state, so a registration that lands after the process is already started still warms
        // the channel.
        observeProcessLifecycle(immediateOnMain = true, onStart = ::warmUp)
    }

    private fun warmUp() {
        // getState(true) requests a connection attempt if idle,
        // pre-connecting TCP + TLS + HTTP/2 in the background
        managedChannels.onEach { it.getState(true) }
    }
}
