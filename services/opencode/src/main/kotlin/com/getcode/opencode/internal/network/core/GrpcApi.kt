package com.getcode.opencode.internal.network.core

import io.grpc.ManagedChannel
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KFunction2

abstract class GrpcApi(protected val managedChannel: ManagedChannel)