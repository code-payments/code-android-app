package com.getcode.oct24.internal.network.core;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J0\u0010\u0002\u001a\b\u0012\u0004\u0012\u0002H\u00040\u0003\"\b\b\u0000\u0010\u0004*\u00020\u00012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u0002H\u00040\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u0007H&J&\u0010\u0002\u001a\b\u0012\u0004\u0012\u0002H\u00040\u0003\"\b\b\u0000\u0010\u0004*\u00020\u00012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u0002H\u00040\bH\u0016J0\u0010\u0002\u001a\b\u0012\u0004\u0012\u0002H\u00040\t\"\b\b\u0000\u0010\u0004*\u00020\u00012\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u0002H\u00040\t2\b\b\u0002\u0010\u0006\u001a\u00020\u0007H&\u00a8\u0006\n"}, d2 = {"Lcom/getcode/oct24/internal/network/core/NetworkOracle;", "", "managedRequest", "Lio/reactivex/rxjava3/core/Flowable;", "ResponseType", "request", "timeout", "", "Lio/reactivex/rxjava3/core/Single;", "Lkotlinx/coroutines/flow/Flow;", "flipchat_debug"})
public abstract interface NetworkOracle {
    
    @org.jetbrains.annotations.NotNull()
    public abstract <ResponseType extends java.lang.Object>io.reactivex.rxjava3.core.Flowable<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
    io.reactivex.rxjava3.core.Single<ResponseType> request);
    
    @org.jetbrains.annotations.NotNull()
    public abstract <ResponseType extends java.lang.Object>io.reactivex.rxjava3.core.Flowable<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
    io.reactivex.rxjava3.core.Flowable<ResponseType> request, long timeout);
    
    @org.jetbrains.annotations.NotNull()
    public abstract <ResponseType extends java.lang.Object>kotlinx.coroutines.flow.Flow<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.flow.Flow<? extends ResponseType> request, long timeout);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        @org.jetbrains.annotations.NotNull()
        public static <ResponseType extends java.lang.Object>io.reactivex.rxjava3.core.Flowable<ResponseType> managedRequest(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.network.core.NetworkOracle $this, @org.jetbrains.annotations.NotNull()
        io.reactivex.rxjava3.core.Single<ResponseType> request) {
            return null;
        }
    }
}