package com.getcode.oct24.internal.network.core;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\b&\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004JI\u0010\u0007\u001a\b\u0012\u0004\u0012\u0002H\t0\b\"\b\b\u0000\u0010\n*\u00020\u0001\"\b\b\u0001\u0010\t*\u00020\u0001*\u001a\u0012\u0004\u0012\u0002H\n\u0012\n\u0012\b\u0012\u0004\u0012\u0002H\t0\f\u0012\u0004\u0012\u00020\r0\u000b2\u0006\u0010\u000e\u001a\u0002H\n\u00a2\u0006\u0002\u0010\u000fR\u0014\u0010\u0002\u001a\u00020\u0003X\u0084\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0010"}, d2 = {"Lcom/getcode/oct24/internal/network/core/GrpcApi;", "", "managedChannel", "Lio/grpc/ManagedChannel;", "(Lio/grpc/ManagedChannel;)V", "getManagedChannel", "()Lio/grpc/ManagedChannel;", "callAsCancellableFlow", "Lkotlinx/coroutines/flow/Flow;", "Response", "Request", "Lkotlin/reflect/KFunction2;", "Lio/grpc/stub/StreamObserver;", "", "request", "(Lkotlin/reflect/KFunction;Ljava/lang/Object;)Lkotlinx/coroutines/flow/Flow;", "flipchat_debug"})
public abstract class GrpcApi {
    @org.jetbrains.annotations.NotNull()
    private final io.grpc.ManagedChannel managedChannel = null;
    
    public GrpcApi(@org.jetbrains.annotations.NotNull()
    io.grpc.ManagedChannel managedChannel) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    protected final io.grpc.ManagedChannel getManagedChannel() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final <Request extends java.lang.Object, Response extends java.lang.Object>kotlinx.coroutines.flow.Flow<Response> callAsCancellableFlow(@org.jetbrains.annotations.NotNull()
    kotlin.reflect.KFunction<kotlin.Unit> $this$callAsCancellableFlow, @org.jetbrains.annotations.NotNull()
    Request request) {
        return null;
    }
}