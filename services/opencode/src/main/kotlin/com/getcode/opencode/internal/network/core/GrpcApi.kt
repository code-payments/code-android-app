package com.getcode.opencode.internal.network.core

import io.grpc.ManagedChannel

abstract class GrpcApi(protected val managedChannel: ManagedChannel)