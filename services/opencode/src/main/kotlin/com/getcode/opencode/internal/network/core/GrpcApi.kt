package com.getcode.opencode.internal.network.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.grpc.ManagedChannel

abstract class GrpcApi(protected val managedChannel: ManagedChannel): DefaultLifecycleObserver {

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }


    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        warmUp()
    }

    private fun warmUp() {
        managedChannel.getState(true)
    }
}