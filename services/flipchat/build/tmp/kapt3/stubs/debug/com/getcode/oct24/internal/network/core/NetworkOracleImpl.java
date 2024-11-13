package com.getcode.oct24.internal.network.core;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\u0018\u0002\n\u0000\b\u0000\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J.\u0010\u0006\u001a\b\u0012\u0004\u0012\u0002H\b0\u0007\"\b\b\u0000\u0010\b*\u00020\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u0002H\b0\u00072\u0006\u0010\u000b\u001a\u00020\fH\u0016J.\u0010\u0006\u001a\b\u0012\u0004\u0012\u0002H\b0\r\"\b\b\u0000\u0010\b*\u00020\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u0002H\b0\r2\u0006\u0010\u000b\u001a\u00020\fH\u0016R\u0013\u0010\u0003\u001a\u00070\u0004\u00a2\u0006\u0002\b\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/getcode/oct24/internal/network/core/NetworkOracleImpl;", "Lcom/getcode/oct24/internal/network/core/NetworkOracle;", "()V", "scheduler", "Lio/reactivex/rxjava3/core/Scheduler;", "Lio/reactivex/rxjava3/annotations/NonNull;", "managedRequest", "Lio/reactivex/rxjava3/core/Flowable;", "ResponseType", "", "request", "timeout", "", "Lkotlinx/coroutines/flow/Flow;", "flipchat_debug"})
public final class NetworkOracleImpl implements com.getcode.oct24.internal.network.core.NetworkOracle {
    @org.jetbrains.annotations.NotNull()
    private final io.reactivex.rxjava3.core.Scheduler scheduler = null;
    
    public NetworkOracleImpl() {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public <ResponseType extends java.lang.Object>io.reactivex.rxjava3.core.Flowable<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
    io.reactivex.rxjava3.core.Flowable<ResponseType> request, long timeout) {
        return null;
    }
    
    @java.lang.Override()
    @kotlin.OptIn(markerClass = {kotlinx.coroutines.FlowPreview.class})
    @org.jetbrains.annotations.NotNull()
    public <ResponseType extends java.lang.Object>kotlinx.coroutines.flow.Flow<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.flow.Flow<? extends ResponseType> request, long timeout) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public <ResponseType extends java.lang.Object>io.reactivex.rxjava3.core.Flowable<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
    io.reactivex.rxjava3.core.Single<ResponseType> request) {
        return null;
    }
}