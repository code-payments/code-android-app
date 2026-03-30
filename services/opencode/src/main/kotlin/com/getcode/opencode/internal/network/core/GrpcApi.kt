package com.getcode.opencode.internal.network.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.grpc.ManagedChannel

abstract class GrpcApi(protected val managedChannels: List<ManagedChannel>): DefaultLifecycleObserver {

    constructor(managedChannel: ManagedChannel): this(listOf(managedChannel))
    constructor(vararg managedChannels: ManagedChannel): this(managedChannels.toList())

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        warmUp()
    }

    private fun warmUp() {
        managedChannels.onEach { it.enterIdle() }
    }
}
