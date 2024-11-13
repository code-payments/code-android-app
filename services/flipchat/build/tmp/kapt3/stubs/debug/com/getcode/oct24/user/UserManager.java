package com.getcode.oct24.user;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001:\u0001\u001bB\u0007\b\u0007\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0018\u001a\u00020\u0019J\u000e\u0010\u001a\u001a\u00020\u00192\u0006\u0010\n\u001a\u00020\u000bJ(\u0010\u001a\u001a\u00020\u00192\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\u0012\u001a\f\u0012\u0004\u0012\u00020\u00140\u0013j\u0002`\u00152\u0006\u0010\u0006\u001a\u00020\u0007J\u0018\u0010\u001a\u001a\u00020\u00192\u0010\u0010\u0012\u001a\f\u0012\u0004\u0012\u00020\u00140\u0013j\u0002`\u0015J\u000e\u0010\u001a\u001a\u00020\u00192\u0006\u0010\u0006\u001a\u00020\u0007R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u00078F\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\tR\u0013\u0010\n\u001a\u0004\u0018\u00010\u000b8F\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\rR\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00050\u000f8F\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R\u001f\u0010\u0012\u001a\u0010\u0012\u0004\u0012\u00020\u0014\u0018\u00010\u0013j\u0004\u0018\u0001`\u00158F\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0017\u00a8\u0006\u001c"}, d2 = {"Lcom/getcode/oct24/user/UserManager;", "", "()V", "_state", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/getcode/oct24/user/UserManager$State;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "keyPair", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "getKeyPair", "()Lcom/getcode/ed25519/Ed25519$KeyPair;", "state", "Lkotlinx/coroutines/flow/StateFlow;", "getState", "()Lkotlinx/coroutines/flow/StateFlow;", "userId", "", "", "Lcom/getcode/model/ID;", "getUserId", "()Ljava/util/List;", "clear", "", "set", "State", "flipchat_debug"})
public final class UserManager {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.getcode.oct24.user.UserManager.State> _state = null;
    
    @javax.inject.Inject()
    public UserManager() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.getcode.oct24.user.UserManager.State> getState() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.getcode.ed25519.Ed25519.KeyPair getKeyPair() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.util.List<java.lang.Byte> getUserId() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getDisplayName() {
        return null;
    }
    
    public final void set(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair keyPair) {
    }
    
    public final void set(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId) {
    }
    
    public final void set(@org.jetbrains.annotations.NotNull()
    java.lang.String displayName) {
    }
    
    public final void set(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair keyPair, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.NotNull()
    java.lang.String displayName) {
    }
    
    public final void clear() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B5\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0016\b\u0002\u0010\u0004\u001a\u0010\u0012\u0004\u0012\u00020\u0006\u0018\u00010\u0005j\u0004\u0018\u0001`\u0007\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\u0002\u0010\nJ\u000b\u0010\u0011\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0017\u0010\u0012\u001a\u0010\u0012\u0004\u0012\u00020\u0006\u0018\u00010\u0005j\u0004\u0018\u0001`\u0007H\u00c6\u0003J\u000b\u0010\u0013\u001a\u0004\u0018\u00010\tH\u00c6\u0003J9\u0010\u0014\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\u0016\b\u0002\u0010\u0004\u001a\u0010\u0012\u0004\u0012\u00020\u0006\u0018\u00010\u0005j\u0004\u0018\u0001`\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\tH\u00c6\u0001J\u0013\u0010\u0015\u001a\u00020\u00162\b\u0010\u0017\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0018\u001a\u00020\u0019H\u00d6\u0001J\t\u0010\u001a\u001a\u00020\tH\u00d6\u0001R\u0013\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u001f\u0010\u0004\u001a\u0010\u0012\u0004\u0012\u00020\u0006\u0018\u00010\u0005j\u0004\u0018\u0001`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001b"}, d2 = {"Lcom/getcode/oct24/user/UserManager$State;", "", "keyPair", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "userId", "", "", "Lcom/getcode/model/ID;", "displayName", "", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Ljava/lang/String;)V", "getDisplayName", "()Ljava/lang/String;", "getKeyPair", "()Lcom/getcode/ed25519/Ed25519$KeyPair;", "getUserId", "()Ljava/util/List;", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "flipchat_debug"})
    public static final class State {
        @org.jetbrains.annotations.Nullable()
        private final com.getcode.ed25519.Ed25519.KeyPair keyPair = null;
        @org.jetbrains.annotations.Nullable()
        private final java.util.List<java.lang.Byte> userId = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String displayName = null;
        
        public State(@org.jetbrains.annotations.Nullable()
        com.getcode.ed25519.Ed25519.KeyPair keyPair, @org.jetbrains.annotations.Nullable()
        java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.Nullable()
        java.lang.String displayName) {
            super();
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.getcode.ed25519.Ed25519.KeyPair getKeyPair() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.util.List<java.lang.Byte> getUserId() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getDisplayName() {
            return null;
        }
        
        public State() {
            super();
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.getcode.ed25519.Ed25519.KeyPair component1() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.util.List<java.lang.Byte> component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.getcode.oct24.user.UserManager.State copy(@org.jetbrains.annotations.Nullable()
        com.getcode.ed25519.Ed25519.KeyPair keyPair, @org.jetbrains.annotations.Nullable()
        java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.Nullable()
        java.lang.String displayName) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}